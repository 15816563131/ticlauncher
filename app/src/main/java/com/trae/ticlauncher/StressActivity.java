package com.trae.ticlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * ColorOS 手表：压力（复刻 HeyStress）。
 *
 * OPPO 原版用 PPG 的 HRV（逐拍间期）算压力，标准 Android API 拿不到逐拍数据，
 * 这里用「心率均值 + 心率波动」做等价近似：心率越高、波动越大 → 压力越高。
 *
 * 省电要点：仅在用户点击测量的 30 秒内注册心率传感器，结束/离开立刻注销。
 */
public class StressActivity extends Activity implements SensorEventListener {

    private static final int REQ_BODY = 201;
    private static final long MEASURE_MS = 30_000L;

    private TextView mValue, mLevel, mHint;
    private View mBar;

    private SensorManager mSm;
    private Sensor mHeart;

    private boolean mMeasuring;
    private final List<Integer> mSamples = new ArrayList<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stress);

        mValue = findViewById(R.id.st_value);
        mLevel = findViewById(R.id.st_level);
        mHint = findViewById(R.id.st_hint);
        mBar = findViewById(R.id.st_bar);

        mSm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeart = mSm != null ? mSm.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;

        findViewById(R.id.st_value).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startMeasure(); }
        });
        mLevel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startMeasure(); }
        });

        if (checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMeasure();
    }

    private void startMeasure() {
        if (mMeasuring) return;
        if (mHeart == null) {
            mLevel.setText("设备不支持心率");
            return;
        }
        mMeasuring = true;
        mSamples.clear();
        mValue.setText("--");
        mLevel.setText("测量中，请保持静止…");
        mLevel.setTextColor(0xFFFF7D08);
        // 仅测量期间用 UI 速率，30 秒后自动停
        mSm.registerListener(this, mHeart, SensorManager.SENSOR_DELAY_UI);
        mHandler.postDelayed(new Runnable() {
            @Override public void run() { finishMeasure(); }
        }, MEASURE_MS);
    }

    private void stopMeasure() {
        mMeasuring = false;
        if (mSm != null) mSm.unregisterListener(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    private void finishMeasure() {
        if (!mMeasuring) return;
        stopMeasure();
        if (mSamples.isEmpty()) {
            mValue.setText("--");
            mLevel.setText("未获取到数据，请重试");
            mLevel.setTextColor(0xFF9A9A9A);
            return;
        }
        int stress = computeStress();
        renderResult(stress);
    }

    /** 近似压力：心率越高、心率波动越大 → 压力越高（0~100）。 */
    private int computeStress() {
        double mean = 0;
        for (int v : mSamples) mean += v;
        mean /= mSamples.size();
        double var = 0;
        for (int v : mSamples) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / mSamples.size());

        double index = (mean - 55) * 1.6 + std * 5.0;
        if (index < 0) index = 0;
        if (index > 100) index = 100;
        return (int) Math.round(index);
    }

    private void renderResult(int stress) {
        mValue.setText(String.valueOf(stress));
        String level;
        int color;
        if (stress < 30) { level = "放松"; color = 0xFF41D97A; }
        else if (stress < 60) { level = "正常"; color = 0xFF66AAEA; }
        else if (stress < 80) { level = "中等"; color = 0xFFFE7C48; }
        else { level = "偏高"; color = 0xFFED5844; }
        mLevel.setText(level);
        mLevel.setTextColor(color);
        mValue.setTextColor(color);
        mHint.setText("点击数值重新测量");

        // 等级条宽度
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mBar.getLayoutParams();
        float density = getResources().getDisplayMetrics().density;
        lp.width = (int) (150f * density * (stress / 100f));
        mBar.setLayoutParams(lp);
        mBar.setBackgroundColor(color);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mMeasuring || event.sensor.getType() != Sensor.TYPE_HEART_RATE) return;
        int bpm = Math.round(event.values[0]);
        if (bpm > 0 && bpm < 255) {
            mSamples.add(bpm);
            mValue.setText(String.valueOf(bpm));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
