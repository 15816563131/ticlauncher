package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 负一屏：健康卡片（步数 / 心率 / 消耗）。
 *
 * 使用 XML 布局 + oppoicons drawable，所有数据更新通过 ValueAnimator 平滑过渡。
 * 步数取自 Android 标准 TYPE_STEP_COUNTER 传感器，每日零点自动归零；
 * 心率取自 PPG TYPE_HEART_RATE。只在页面可见时注册传感器监听。
 */
public class HealthPanelView extends LinearLayout implements SensorEventListener {

    private TextView mValSteps, mValHeart, mValCalorie;
    private ProgressBar mProgSteps;

    private SensorManager mSensorManager;
    private boolean mActive;

    private int mSteps = -1;
    private int mGoal = 8000;
    private int mCalorie = -1;
    private int mHeart = -1;

    /** 是否请求过权限（只请求一次） */
    private static boolean sRequestedPerms = false;

    private ValueAnimator mStepsAnim;
    private ValueAnimator mHeartAnim;
    private ValueAnimator mCalorieAnim;

    public HealthPanelView(Context context) {
        this(context, null);
    }

    public HealthPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HealthPanelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.panel_health, this, true);
        setOrientation(VERTICAL);
        setBackgroundResource(0); // 布局文件自己管背景

        mValSteps = findViewById(R.id.val_steps);
        mValHeart = findViewById(R.id.val_heart);
        mValCalorie = findViewById(R.id.val_calorie);
        mProgSteps = findViewById(R.id.prog_steps);

        // 初始占位显示
        mValSteps.setText("--");
        mValHeart.setText("--");
        mValCalorie.setText("--");
        mProgSteps.setProgress(0);
    }

    /** 只在本页可见时才工作，避免长期耗电。 */
    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (active) {
            requestAllPermissions();
            registerAllSensors();
        } else {
            unregister();
            cancelAnims();
        }
    }

    private void requestAllPermissions() {
        if (sRequestedPerms) return;
        Context ctx = getContext();
        if (!(ctx instanceof android.app.Activity)) return;

        String[] needed = new String[0];
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            needed = append(needed, Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            needed = append(needed, Manifest.permission.BODY_SENSORS);
        }
        if (needed.length > 0) {
            sRequestedPerms = true;
            ActivityCompat.requestPermissions((android.app.Activity) ctx, needed, 200);
        }
    }

    private static String[] append(String[] arr, String s) {
        String[] out = new String[arr.length + 1];
        System.arraycopy(arr, 0, out, 0, arr.length);
        out[arr.length] = s;
        return out;
    }

    private void registerAllSensors() {
        if (mSensorManager == null) {
            mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        }
        if (mSensorManager == null) return;

        Sensor step = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (step != null) {
            mSensorManager.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED) {
            Sensor heart = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (heart != null) {
                mSensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void unregister() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregister();
        cancelAnims();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mActive) return;
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            StepSource d = StepSource.compute(getContext(), event.values[0]);
            if (d.valid()) {
                animateSteps(d.steps, d.goal, d.calorie);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int bpm = Math.round(event.values[0]);
            if (bpm > 0 && bpm != mHeart) {
                animateHeart(bpm);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---------- 平滑过渡动画 ----------

    private void animateSteps(int newSteps, int newGoal, int newCalorie) {
        int from = mSteps < 0 ? 0 : mSteps;
        mSteps = newSteps;
        mGoal = newGoal;
        mCalorie = newCalorie;

        if (mStepsAnim != null) mStepsAnim.cancel();
        mStepsAnim = ValueAnimator.ofInt(from, newSteps);
        mStepsAnim.setDuration(500);
        mStepsAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                int v = (int) a.getAnimatedValue();
                mValSteps.setText(String.valueOf(v));
                int prog = mGoal > 0 ? Math.min(100, v * 100 / mGoal) : 0;
                mProgSteps.setProgress(prog);
            }
        });
        mStepsAnim.start();

        if (mCalorieAnim != null) mCalorieAnim.cancel();
        int fromC = mCalorie < 0 ? 0 : Math.round(mCalorie / 2f);
        mCalorieAnim = ValueAnimator.ofInt(fromC, newCalorie);
        mCalorieAnim.setDuration(500);
        mCalorieAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                mValCalorie.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        mCalorieAnim.start();
    }

    private void animateHeart(int newHeart) {
        int from = mHeart < 0 ? 0 : mHeart;
        mHeart = newHeart;
        if (mHeartAnim != null) mHeartAnim.cancel();
        mHeartAnim = ValueAnimator.ofInt(from, newHeart);
        mHeartAnim.setDuration(300);
        mHeartAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                mValHeart.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        mHeartAnim.start();
    }

    private void cancelAnims() {
        if (mStepsAnim != null) mStepsAnim.cancel();
        if (mHeartAnim != null) mHeartAnim.cancel();
        if (mCalorieAnim != null) mCalorieAnim.cancel();
    }
}
