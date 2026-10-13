package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
 * 界面按官方 res/layout/fragment_hr_measure.xml + res/values-zh-rCN/strings.xml 还原。
 *
 * 省电与离腕逻辑（P0-2）：
 *  · 同时注册 {@link Sensor#TYPE_LOW_LATENCY_OFFBODY_DETECT} 离腕检测传感器；
 *  · 检测到「离腕」立即停掉 PPG 采样并显示「未佩戴」，绝不空转；
 *  · 检测到「佩戴」自动重新启动测量（不用手动点）；
 *  · 设置里可选自动测量间隔：连续 / 1 分钟 / 5 分钟 / 10 分钟 / 关闭；
 *    非连续模式下按间隔突发采样（测一阵→停一阵），间隔脉冲用
 *    {@link AlarmManager#setExactAndAllowWhileIdle} 而不是 postDelayed（见 HANDOVER P0-2）。
 */
public class HeartRateActivity extends Activity implements SensorEventListener {

    private static final int REQ_BODY = 101;
    /** 周期脉冲 action（AlarmManager → 动态注册的 Receiver）。 */
    private static final String ACTION_HR_TICK = "com.trae.ticlauncher.action.HR_TICK";
    /** 非连续模式下一次突发测量的最长时长（ms），到点主动停 PPG 省电。 */
    private static final long BURST_MS = 12000L;

    private View mHeart;
    private TextView mTvValue, mTvStatus;

    private SensorManager mSensorManager;
    private Sensor mHeartSensor;
    private Sensor mOffBodySensor;
    /** 当前是否正处于一次突发测量中。 */
    private boolean mBursting;
    private boolean mGranted;
    private boolean mResumed;
    /** 佩戴状态：默认佩戴，等离腕传感器首次上报告知；设备不支持离腕传感器时恒为 true。 */
    private boolean mOnBody = true;
    private int mBpm = -1;

    /** 自动测量间隔（分钟）：0=连续，1/5/10=按间隔突发，-1=关闭（仅手动）。 */
    private int mIntervalMin = 0;

    private AlarmManager mAlarm;
    private PendingIntent mTickPi;
    private final Handler mBurstHandler = new Handler();
    /** 用户手动点停后，本会话内不再自动续测，直到下次 onResume。 */
    private boolean mManualStop = false;

    private ValueAnimator mBeatAnim;
    private ValueAnimator mBpmAnim;

    /** 离腕脉冲 Receiver：到点且仍佩戴、页面在前台时启动一次突发测量。 */
    private final BroadcastReceiver mTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_HR_TICK.equals(intent.getAction())
                    && mOnBody && mResumed && !mBursting
                    && mIntervalMin > 0 && !mManualStop) {
                startBurst(true);
            }
        }
    };

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
        // 离腕检测传感器（API 24+，TicWatch Pro 实测存在）：拿不到就退化为「一直佩戴」。
        mOffBodySensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) : null;

        mAlarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent tick = new Intent(ACTION_HR_TICK);
        tick.setPackage(getPackageName());
        mTickPi = PendingIntent.getBroadcast(this, 0, tick,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            registerReceiver(mTickReceiver, new IntentFilter(ACTION_HR_TICK));
        } catch (Exception ignored) {
        }

        mIntervalMin = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getInt(SettingsActivity.KEY_HR_INTERVAL, 0);

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
            if (mGranted) onResumeMeasure();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        mManualStop = false;
        onResumeMeasure();
    }

    /** 进入前台：注册离腕传感器；若已授权、佩戴且未关闭自动，则启动测量。 */
    private void onResumeMeasure() {
        if (mGranted && mHeartSensor != null) {
            registerOffBody();
            if (mOnBody && mIntervalMin != -1) {
                startBurst(true);
            } else if (!mOnBody) {
                showOffBody(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        // 离开页面：停测量、停突发、撤掉脉冲、注销全部传感器，绝不后台空转
        mBurstHandler.removeCallbacks(mEndBurst);
        cancelHrTick();
        stopMeasure();
        unregisterOffBody();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBurstHandler.removeCallbacks(mEndBurst);
        cancelHrTick();
        try { unregisterReceiver(mTickReceiver); } catch (Exception ignored) {}
        stopBeatAnimation();
        cancelBpmAnim();
    }

    private void toggleMeasure() {
        if (mBursting) {
            // 手动点停：结束本次突发且本会话不再自动续测
            mManualStop = true;
            endBurst(false);
        } else {
            // 手动点火：测一次（非连续模式下测完不自动续测）
            startBurst(false);
        }
    }

    /** 启动一次测量突发。auto=true 表示由系统/佩戴自动触发（会按间隔续测）。 */
    private void startBurst(boolean auto) {
        if (mHeartSensor == null) {
            mTvStatus.setText("设备不支持心率");
            return;
        }
        if (!mOnBody) { showOffBody(true); return; }
        if (!mGranted) return;
        mBursting = true;
        mManualStop = !auto;   // 手动点火 → 测完不自动续测；自动触发 → 允许按间隔续测
        mTvStatus.setText("正在测量");
        mTvStatus.setTextColor(0xFFFF5416);
        setHeartDim(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startBeatAnimation();
        mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
        // 非连续模式：到点主动停 PPG 省电；连续模式一直测到离开页面/离腕/手动停。
        mBurstHandler.removeCallbacks(mEndBurst);
        if (mIntervalMin > 0) {
            mBurstHandler.postDelayed(mEndBurst, BURST_MS);
        }
    }

    /** 结束一次突发测量。reschedule=true 时按间隔排下一轮脉冲。 */
    private void endBurst(boolean reschedule) {
        mBursting = false;
        mBurstHandler.removeCallbacks(mEndBurst);
        mSensorManager.unregisterListener(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopBeatAnimation();
        if (mHeart != null) { mHeart.setScaleX(1f); mHeart.setScaleY(1f); }
        if (!mOnBody) {
            showOffBody(false);
            return;
        }
        mTvStatus.setText(mHeartSensor == null ? "设备不支持心率" : (mManualStop ? "已停止" : "已佩戴"));
        mTvStatus.setTextColor(0xFFB0B5BF);
        if (reschedule && mOnBody && mResumed && mIntervalMin > 0 && !mManualStop) {
            scheduleHrTick(mIntervalMin);
        }
    }

    /** 离腕：立刻停 PPG 并显示「未佩戴」。 */
    private void showOffBody(boolean fromEvent) {
        mBursting = false;
        mBurstHandler.removeCallbacks(mEndBurst);
        mSensorManager.unregisterListener(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopBeatAnimation();
        if (mHeart != null) { mHeart.setScaleX(1f); mHeart.setScaleY(1f); }
        mTvStatus.setText("未佩戴");
        mTvStatus.setTextColor(0xFFB0B5BF);
        setHeartDim(true);
    }

    private void setHeartDim(boolean dim) {
        if (mHeart != null) mHeart.setAlpha(dim ? 0.35f : 1f);
    }

    /** 用 AlarmManager 排下一轮脉冲（非连续模式）。 */
    private void scheduleHrTick(int minutes) {
        if (mAlarm == null || mTickPi == null) return;
        long at = System.currentTimeMillis() + minutes * 60_000L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mAlarm.setExactAndAllowWhileIdle(AlarmManager.RTC, at, mTickPi);
            } else {
                mAlarm.set(AlarmManager.RTC, at, mTickPi);
            }
        } catch (Exception ignored) {
        }
    }

    private void cancelHrTick() {
        if (mAlarm != null && mTickPi != null) {
            try { mAlarm.cancel(mTickPi); } catch (Exception ignored) {}
        }
    }

    private void registerOffBody() {
        if (mOffBodySensor != null && mSensorManager != null) {
            mSensorManager.registerListener(this, mOffBodySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterOffBody() {
        if (mSensorManager != null && mOffBodySensor != null) {
            // 只注销离腕传感器；心率传感器已在 stopMeasure 中单独处理
            try {
                mSensorManager.unregisterListener(this, mOffBodySensor);
            } catch (Exception ignored) {}
        }
    }

    /** 突发到点：停 PPG，并按间隔续测。 */
    private final Runnable mEndBurst = new Runnable() {
        @Override public void run() { endBurst(true); }
    };

    /** 兼容旧逻辑：onPause 调用。 */
    private void stopMeasure() {
        mBursting = false;
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        mTvStatus.setText(mHeartSensor == null ? "设备不支持心率" : (mOnBody ? "已佩戴" : "未佩戴"));
        mTvStatus.setTextColor(0xFFFF5416);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopBeatAnimation();
        if (mHeart != null) { mHeart.setScaleX(1f); mHeart.setScaleY(1f); }
    }

    // ---------- 传感器回调 ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            // 离腕检测（Android 标准语义）：values[0] == 1.0 表示离腕，0.0 表示佩戴。
            // 因此 on-body = 值 < 0.5（真机实测 TicWatch Pro 遵循此契约）。
            boolean onBody = event.values != null && event.values.length > 0
                    && event.values[0] < 0.5f;
            if (onBody == mOnBody) return;
            mOnBody = onBody;
            if (onBody) {
                setHeartDim(false);
                if (mResumed && mIntervalMin != -1) startBurst(true);
            } else {
                // 离腕：立即停 PPG、停脉冲、显示「未佩戴」
                mBurstHandler.removeCallbacks(mEndBurst);
                cancelHrTick();
                showOffBody(true);
            }
            return;
        }
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
