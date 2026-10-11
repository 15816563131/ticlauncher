package com.trae.ticlauncher;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * ColorOS 手表：指南针（复刻 HeyCompass）。
 * 使用 TYPE_ROTATION_VECTOR → 方位角。无传感器时用模拟旋转。
 */
public class CompassActivity extends Activity implements SensorEventListener {

    private SensorManager mSm;
    private Sensor mRotV;
    private final float[] mR = new float[9];
    private final float[] mOri = new float[3];

    private ImageView mNeedle;
    private TextView mDeg, mDir;
    private float mLastAzimuth = 0f;

    private static final String[] DIRS = {
            "北", "东北", "东", "东南", "南", "西南", "西", "西北"
    };

    private float mMockAngle;
    private final Runnable mMockTick = new Runnable() {
        @Override public void run() {
            mMockAngle += 3f;
            if (mMockAngle >= 360f) mMockAngle -= 360f;
            applyAzimuth(mMockAngle);
            mNeedle.postDelayed(this, 90);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);

        mNeedle = findViewById(R.id.c_needle);
        mDeg = findViewById(R.id.c_deg);
        mDir = findViewById(R.id.c_dir);

        mSm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (mSm != null) {
            mRotV = mSm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRotV != null) {
            mSm.registerListener(this, mRotV, SensorManager.SENSOR_DELAY_UI);
        } else {
            // 无传感器时用模拟旋转演示；onPause 会立即停掉，不在后台空转
            mNeedle.postDelayed(mMockTick, 90);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRotV != null) {
            mSm.unregisterListener(this);
        } else {
            mNeedle.removeCallbacks(mMockTick);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(mR, event.values);
        SensorManager.getOrientation(mR, mOri);
        float azim = (float) Math.toDegrees(mOri[0]);
        if (azim < 0) azim += 360f;
        applyAzimuth(azim);
    }

    private void applyAzimuth(float azim) {
        // 平滑过渡
        float delta = azim - mLastAzimuth;
        // 处理 0/360 跨越
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        float target = mLastAzimuth + delta * 0.25f; // 低通
        if (target < 0) target += 360f;
        if (target >= 360f) target -= 360f;
        mLastAzimuth = target;

        mNeedle.animate()
                .setDuration(120)
                .setInterpolator(new DecelerateInterpolator())
                .rotation(-target) // 视图旋转取反，使北朝上
                .start();

        int deg = Math.round(target);
        mDeg.setText(String.valueOf(deg));
        int idx = Math.round(target / 45f) % 8;
        mDir.setText(DIRS[idx]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
