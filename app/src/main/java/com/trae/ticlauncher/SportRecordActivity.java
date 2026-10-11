package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import java.util.Locale;

/**
 * ColorOS 手表：运动记录页（复刻 HeySports sport_five_data_layout.xml）。
 *
 * 数据绑定 TicWatch Pro 原生传感器（标准 Android API）：
 *   · 心率   Sensor.TYPE_HEART_RATE（PixArt PAH8011）
 *   · 步数   Sensor.TYPE_STEP_COUNTER  → 距离 = 步数 x 0.7m，配速 = 时长/距离
 * 布局与尺寸严格照抄官方 XML（见 activity_sport_record.xml 注释），页面不做自绘。
 */
public class SportRecordActivity extends Activity implements SensorEventListener {

    public static final String EXTRA_SPORT_NAME = "sport_name";
    public static final String EXTRA_DISTANCE = "distance_km";
    public static final String EXTRA_DURATION = "duration_sec";
    public static final String EXTRA_HEART = "avg_heart";
    public static final String EXTRA_CALORIE = "calorie";

    /** 步幅（米/步），与官方健走默认量级一致 */
    private static final float STRIDE_M = 0.7f;

    private TextView mTvDistance, mTvDuration, mTvPace, mTvHeart, mTvCalorie;

    private SensorManager mSm;
    private String mSportName;

    private int mSteps = -1;        // 今日/本次累计（传感器原始值）
    private int mBaseSteps = -1;    // 本次起始基准
    private int mHeart = -1;
    private int mHeartSum = 0;
    private int mHeartCount = 0;

    private long mStartMs;

    private ValueAnimator mHeartAnim;

    private final Handler mHandler = new Handler();

    private final Runnable mTimer = new Runnable() {
        @Override public void run() {
            renderTime();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_record);

        mSportName = getIntent().getStringExtra(EXTRA_SPORT_NAME);
        if (mSportName == null) mSportName = "运动";

        mTvDistance = findViewById(R.id.rec_distance);
        mTvDuration = findViewById(R.id.rec_duration);
        mTvPace = findViewById(R.id.rec_pace);
        mTvHeart = findViewById(R.id.rec_heart);
        mTvCalorie = findViewById(R.id.rec_calorie);

        findViewById(R.id.rec_finish).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishSport(); }
        });

        mSm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 201);
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 202);
        }

        mStartMs = System.currentTimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSm != null) {
            Sensor hr = mSm.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (hr != null) {
                mSm.registerListener(this, hr, SensorManager.SENSOR_DELAY_NORMAL);
            }
            Sensor step = mSm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (step != null) {
                mSm.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
        mHandler.removeCallbacks(mTimer);
        mHandler.post(mTimer);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSm != null) mSm.unregisterListener(this);
        mHandler.removeCallbacks(mTimer);
        cancelHeartAnim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mTimer);
        cancelHeartAnim();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---------- 传感器 ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            int total = Math.round(event.values[0]);
            if (mBaseSteps < 0) {
                mBaseSteps = total;
            }
            mSteps = Math.max(0, total - mBaseSteps);
            renderSteps();
        } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int bpm = Math.round(event.values[0]);
            if (bpm <= 0 || bpm > 255) return;
            mHeartSum += bpm;
            mHeartCount++;
            animateHeart(bpm);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- 渲染 ----------

    private void renderSteps() {
        float km = mSteps * STRIDE_M / 1000f;
        mTvDistance.setText(String.format(Locale.getDefault(), "%.2f", km));
        mTvCalorie.setText(String.valueOf(Math.round(mSteps * 0.04f)));
        renderPace(km);
    }

    private void renderTime() {
        long sec = elapsedSec();
        mTvDuration.setText(String.format(Locale.getDefault(), "%02d:%02d",
                sec / 60, sec % 60));
        renderPace(currentKm());
    }

    private void renderPace(float km) {
        long sec = elapsedSec();
        if (km <= 0.005f || sec <= 0) {
            mTvPace.setText("--");
            return;
        }
        float paceSecPerKm = sec / km;
        int min = (int) (paceSecPerKm / 60);
        int secPart = Math.round(paceSecPerKm - min * 60);
        if (secPart >= 60) { min++; secPart -= 60; }
        mTvPace.setText(String.format(Locale.getDefault(), "%d'%02d\"", min, secPart));
    }

    private void animateHeart(int bpm) {
        int from = mHeart < 0 ? bpm : mHeart;
        mHeart = bpm;
        cancelHeartAnim();
        mHeartAnim = ValueAnimator.ofInt(from, bpm);
        mHeartAnim.setDuration(400);
        mHeartAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mHeartAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                mTvHeart.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        mHeartAnim.start();
    }

    private void cancelHeartAnim() {
        if (mHeartAnim != null) { mHeartAnim.cancel(); mHeartAnim = null; }
    }

    private long elapsedSec() {
        return Math.max(0, (System.currentTimeMillis() - mStartMs) / 1000L);
    }

    private float currentKm() {
        return Math.max(0, mSteps) * STRIDE_M / 1000f;
    }

    private void finishSport() {
        long sec = elapsedSec();
        float km = currentKm();
        int avgHeart = mHeartCount > 0 ? mHeartSum / mHeartCount : Math.max(0, mHeart);

        Intent i = new Intent(this, SportEndActivity.class);
        i.putExtra(EXTRA_SPORT_NAME, mSportName);
        i.putExtra(EXTRA_DISTANCE, km);
        i.putExtra(EXTRA_DURATION, sec);
        i.putExtra(EXTRA_HEART, avgHeart);
        i.putExtra(EXTRA_CALORIE, Math.round(Math.max(0, mSteps) * 0.04f));
        startActivity(i);
        finish();
    }
}
