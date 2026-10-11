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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

/**
 * ColorOS 手表：心率（复刻 HeyHeartRate 测量页）。
 *
 * 数据源：Android 标准 {@link Sensor#TYPE_HEART_RATE}（TicWatch Pro 上对应 PixArt PAH8011）。
 * 完全不引用任何 com.oppo / com.heytap 类或 OPPO 私有框架，只使用标准 Android API（minSdk 28）。
 *
 * 界面按官方 res/layout/fragment_hr_measure.xml + res/values-zh-rCN/strings.xml 还原：
 *   标题「心率」/ 心形图（官方 heart_0.webp）居中叠加大号数值 / 单位「次/分」/ 状态「正在测量」
 * 素材全部取自解包后的 HeyHeartRate APK，页面不做自绘，测量中的心跳感由心形缩放动画表达。
 *
 * 省电：SENSOR_DELAY_NORMAL；亮屏仅在测量期间；onPause 立即注销传感器。
 */
public class HeartRateActivity extends Activity implements SensorEventListener {

    private static final int REQ_BODY = 101;

    private View mHeart;
    private TextView mTvValue, mTvStatus;

    private SensorManager mSensorManager;
    private Sensor mHeartSensor;
    private boolean mMeasuring;
    private boolean mGranted;

    private int mBpm = -1;

    private ValueAnimator mBeatAnim;
    private ValueAnimator mBpmAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heartrate);

        mHeart = findViewById(R.id.hr_heart);
        mTvValue = findViewById(R.id.hr_value);
        mTvStatus = findViewById(R.id.hr_status);

        findViewById(R.id.hr_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.hr_heart_box).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleMeasure(); }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeartSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;
        if (mHeartSensor == null) {
            mTvStatus.setText("设备不支持心率");
        }

        mGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED;
        if (!mGranted) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY);
        }

        setupSwipeToRecord();
    }

    /** 官方为 ViewPager2 两页左右滑动，这里用「左滑进入记录页」还原该交互。 */
    private void setupSwipeToRecord() {
        final GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) { return true; }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float vx, float vy) {
                        if (e1 != null && e2 != null
                                && e1.getX() - e2.getX() > 60 && Math.abs(vx) > Math.abs(vy)) {
                            startActivity(new Intent(HeartRateActivity.this, HrRecordActivity.class));
                            return true;
                        }
                        return false;
                    }
                });
        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                return detector.onTouchEvent(e);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BODY) {
            mGranted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (mGranted) startMeasure();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGranted && mHeartSensor != null) {
            startMeasure();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开页面：停测量、停动画、放掉亮屏，绝不后台空转
        stopMeasure();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBeatAnimation();
        cancelBpmAnim();
    }

    private void toggleMeasure() {
        if (mMeasuring) stopMeasure();
        else startMeasure();
    }

    private void startMeasure() {
        if (mHeartSensor == null) {
            mTvStatus.setText("设备不支持心率");
            return;
        }
        mMeasuring = true;
        mTvStatus.setText("正在测量");
        mTvStatus.setTextColor(0xFFFF5416);  // ColorOS 橙
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startBeatAnimation();
        // NORMAL 足够读心率，比 FASTEST 省电得多
        mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopMeasure() {
        mMeasuring = false;
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        mTvStatus.setText(mHeartSensor == null ? "设备不支持心率" : "点击测量");
        mTvStatus.setTextColor(0xFFFF5416);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopBeatAnimation();
        if (mHeart != null) { mHeart.setScaleX(1f); mHeart.setScaleY(1f); }
    }

    // ---------- 传感器回调 ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HEART_RATE) return;
        int bpm = Math.round(event.values[0]);
        if (bpm <= 0 || bpm > 255) return;
        animateBpm(bpm);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- 数值平滑过渡 ----------

    private void animateBpm(int newBpm) {
        int from = mBpm < 0 ? (newBpm - 1) : mBpm;
        mBpm = newBpm;
        cancelBpmAnim();
        mBpmAnim = ValueAnimator.ofInt(from, newBpm);
        mBpmAnim.setDuration(500);
        mBpmAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mBpmAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                mTvValue.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        mBpmAnim.start();
    }

    private void cancelBpmAnim() {
        if (mBpmAnim != null) { mBpmAnim.cancel(); mBpmAnim = null; }
    }

    // ---------- 心跳感：心形缩放动画（纯素材，无自绘） ----------

    private void startBeatAnimation() {
        stopBeatAnimation();
        if (mHeart == null) return;
        mBeatAnim = ValueAnimator.ofFloat(1f, 1.12f);
        mBeatAnim.setDuration(620);
        mBeatAnim.setRepeatCount(ValueAnimator.INFINITE);
        mBeatAnim.setRepeatMode(ValueAnimator.REVERSE);
        mBeatAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float s = (float) a.getAnimatedValue();
                mHeart.setScaleX(s);
                mHeart.setScaleY(s);
            }
        });
        mBeatAnim.start();
    }

    private void stopBeatAnimation() {
        if (mBeatAnim != null) { mBeatAnim.cancel(); mBeatAnim = null; }
    }
}
