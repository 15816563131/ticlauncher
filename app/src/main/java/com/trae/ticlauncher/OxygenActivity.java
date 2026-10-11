package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;


/**
 * ColorOS 手表：血氧应用（复刻 HeyOxygen）。
 *
 * 纯光电容积脉搏波（PPG）传感器在非运动场景下无法直接输出 SpO2。
 * 本应用使用心率传感器作为占位数据源，后续可接入 HealthService Provider。
 */
public class OxygenActivity extends Activity implements SensorEventListener {

    private TextView mTvValue, mTvStatus;
    private SensorManager mSensorManager;
    private Sensor mHeartSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oxygen);

        mTvValue = findViewById(R.id.ox_value);
        mTvStatus = findViewById(R.id.ox_status);

        findViewById(R.id.ox_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeartSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;

        findViewById(R.id.ox_value).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleMeasure(); }
        });

        if (this.checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 103);
        }
    }

    private boolean mMeasuring;

    private void toggleMeasure() {
        if (mMeasuring) {
            stopMeasure();
        } else {
            if (mHeartSensor == null) {
                mTvStatus.setText("不支持");
                return;
            }
            mMeasuring = true;
            mTvStatus.setText("测量中...");
            mTvStatus.setTextColor(0xFF476CFF);
            // 仅测量期间亮屏；传感器用 NORMAL 省电
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopMeasure() {
        mMeasuring = false;
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        mTvStatus.setText("点击开始测量");
        mTvStatus.setTextColor(0xFF555555);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMeasuring) stopMeasure();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HEART_RATE) return;
        float raw = event.values[0];
        if (raw <= 0) return;
        // 简化：SpO2 估算（实际需要血红蛋白吸收光谱算法）。
        // 正常静息状态下 SpO2 多在 96~100。心率高时略降。
        int spo2 = (int) (98 - Math.max(0, (raw - 80) * 0.1f));
        spo2 = Math.max(90, Math.min(100, spo2));
        animateValue(spo2);
    }

    private int mLastVal = -1;
    private ValueAnimator mValueAnim;

    private void animateValue(int newVal) {
        int from = mLastVal < 0 ? 98 : mLastVal;
        mLastVal = newVal;
        if (mValueAnim != null) mValueAnim.cancel();
        ValueAnimator a = ValueAnimator.ofInt(from, newVal);
        a.setDuration(400);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator av) {
                mTvValue.setText(String.valueOf((int) av.getAnimatedValue()));
            }
        });
        mValueAnim = a;
        a.start();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
