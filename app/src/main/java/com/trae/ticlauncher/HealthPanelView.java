package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
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
 * 心率取自 PPG TYPE_HEART_RATE。只在「桌面在前台 + 本页正显示」时注册传感器监听，
 * 其余时间（切到别的页、被别的应用盖住、息屏）一律注销，避免 PPG 空转耗电。
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

    // ---------- P1-7：负一屏心率可调间隔 + 离腕停测 ----------

    /** 非连续模式下一次突发测量的最长时长（ms），到点主动停 PPG 省电。 */
    private static final long HR_BURST_MS = 12000L;

    private Sensor mHeartSensor;
    private Sensor mOffBodySensor;
    /** 佩戴状态：默认佩戴，等离腕传感器首次上报告知；设备不支持时恒为 true。 */
    private boolean mOnBody = true;
    /** 负一屏心率刷新（秒）：0=连续，30/60=按间隔突发，-1=关闭。 */
    private int mHrPanelInterval = 0;
    /** 当前是否处于一次突发测量中。 */
    private boolean mHrBursting = false;
    private Handler mHrHandler;
    private final Runnable mHrEndBurst = new Runnable() {
        @Override public void run() { endHrBurst(true); }
    };
    private final Runnable mHrReschedule = new Runnable() {
        @Override public void run() { startHrBurst(); }
    };

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

        mHrHandler = new Handler();
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

        // 离腕检测传感器（API 24+）：拿不到就退化为「一直佩戴」。
        mOffBodySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT);
        if (mOffBodySensor != null) {
            mSensorManager.registerListener(this, mOffBodySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // 心率传感器（需 BODY_SENSORS 权限）；按设置里的间隔决定注册方式。
        mHeartSensor = (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED)
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;

        mHrPanelInterval = getContext().getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(SettingsActivity.KEY_HR_PANEL_INTERVAL, 0);

        startHrByInterval();
    }

    /** 按负一屏心率间隔设置启动测量：连续 / 突发 / 关闭。 */
    private void startHrByInterval() {
        if (mHeartSensor == null || !mOnBody) return;
        if (mHrHandler != null) {
            mHrHandler.removeCallbacks(mHrEndBurst);
            mHrHandler.removeCallbacks(mHrReschedule);
        }
        if (mHrPanelInterval < 0) {
            // 关闭：不注册心率，显示占位。
            mValHeart.setText("--");
            mValHeart.setAlpha(1f);
            return;
        }
        mValHeart.setAlpha(1f);
        if (mHrPanelInterval == 0) {
            // 连续：一直注册，NORMAL 档足够且省电（负一屏只是概览）。
            mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            startHrBurst();
        }
    }

    /** 启动一次突发测量（测一阵→停一阵）。 */
    private void startHrBurst() {
        if (mHeartSensor == null || !mOnBody || !mActive) return;
        mHrBursting = true;
        mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (mHrHandler != null) {
            mHrHandler.removeCallbacks(mHrEndBurst);
            mHrHandler.postDelayed(mHrEndBurst, HR_BURST_MS);
        }
    }

    /** 结束一次突发测量。reschedule=true 时按间隔排下一轮。 */
    private void endHrBurst(boolean reschedule) {
        mHrBursting = false;
        if (mHrHandler != null) {
            mHrHandler.removeCallbacks(mHrEndBurst);
            mHrHandler.removeCallbacks(mHrReschedule);
        }
        if (mSensorManager != null && mHeartSensor != null) {
            mSensorManager.unregisterListener(this, mHeartSensor);
        }
        if (reschedule && mActive && mOnBody && mHeartSensor != null
                && mHrPanelInterval > 0 && mHrHandler != null) {
            mHrHandler.postDelayed(mHrReschedule, mHrPanelInterval * 1000L);
        }
    }

    /** 离腕：立刻停 PPG 并显示「未佩戴」。 */
    private void stopHrImmediate() {
        mHrBursting = false;
        if (mHrHandler != null) {
            mHrHandler.removeCallbacks(mHrEndBurst);
            mHrHandler.removeCallbacks(mHrReschedule);
        }
        if (mSensorManager != null && mHeartSensor != null) {
            mSensorManager.unregisterListener(this, mHeartSensor);
        }
        mValHeart.setText("未佩戴");
        mValHeart.setAlpha(0.4f);
    }

    private void unregister() {
        if (mHrHandler != null) {
            mHrHandler.removeCallbacks(mHrEndBurst);
            mHrHandler.removeCallbacks(mHrReschedule);
        }
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
            // 步数传感器每走一步都会回调，值没变就不重开动画，省掉大量无谓的 ValueAnimator
            if (d.valid() && (d.steps != mSteps || d.goal != mGoal || d.calorie != mCalorie)) {
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
