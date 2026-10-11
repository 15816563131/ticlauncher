package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * ColorOS 手表：轻智能模式（超低功耗表盘）。
 *
 * 与普通表盘的区别在于「极简 + 极省电」：
 *  1) 窗口亮度压到最低，不再常亮，屏幕到点即灭；
 *  2) 时钟只在「分钟边界」刷新一次，不做每秒重绘；
 *  3) 步数只在进入时读一次（收到首帧立刻注销传感器）；
 *  4) 电量读系统粘性广播，不注册常驻接收器。
 * 结果：单次刷新开销 ≈ 每分钟一次文本更新，续航可延展数天。
 */
public class SmartModeActivity extends Activity implements SensorEventListener {

    private TextView mTime, mDate, mSteps, mBattery;
    private ImageView mBatteryIcon;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            updateTime();
            // 对齐到下一分钟，避免每秒唤醒
            long delay = 60_000L - (System.currentTimeMillis() % 60_000L);
            mHandler.postDelayed(this, delay);
        }
    };

    private SensorManager mSm;
    private Sensor mStepSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_mode);

        mTime = findViewById(R.id.sm_time);
        mDate = findViewById(R.id.sm_date);
        mSteps = findViewById(R.id.sm_steps);
        mBattery = findViewById(R.id.sm_battery);
        mBatteryIcon = findViewById(R.id.sm_battery_icon);

        // 极低亮度：这是轻智能模式最直接的省电手段
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.12f;
        getWindow().setAttributes(lp);

        // 点击任意位置退出
        findViewById(R.id.sm_root).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        readBatteryOnce();
        mSm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mStepSensor = mSm != null ? mSm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTick.run();
        if (mStepSensor != null) {
            mSm.registerListener(this, mStepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTick);
        if (mSm != null) mSm.unregisterListener(this);
    }

    private void updateTime() {
        Calendar c = Calendar.getInstance();
        mTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));

        String[] week = {"日", "一", "二", "三", "四", "五", "六"};
        mDate.setText(String.format(Locale.getDefault(), "%d月%d日 周%s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                week[c.get(Calendar.DAY_OF_WEEK) - 1]));

        readBatteryOnce();
    }

    private void readBatteryOnce() {
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat == null) return;
        int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return;
        int pct = level * 100 / scale;
        mBattery.setText(pct + "%");
        mBatteryIcon.setImageResource(batteryIcon(pct));
    }

    private int batteryIcon(int pct) {
        if (pct >= 95) return R.drawable.ic_battery_100;
        if (pct >= 85) return R.drawable.ic_battery_90;
        if (pct >= 75) return R.drawable.ic_battery_80;
        if (pct >= 65) return R.drawable.ic_battery_70;
        if (pct >= 55) return R.drawable.ic_battery_60;
        if (pct >= 45) return R.drawable.ic_battery_50;
        if (pct >= 35) return R.drawable.ic_battery_40;
        if (pct >= 25) return R.drawable.ic_battery_30;
        if (pct >= 18) return R.drawable.ic_battery_20;
        if (pct >= 13) return R.drawable.ic_battery_15;
        return R.drawable.ic_battery_10;
    }

    // ---------- 步数：收到首帧即注销，只读一次 ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        StepSource d = StepSource.compute(this, event.values[0]);
        mSteps.setText(d.steps + " 步");
        if (mSm != null) mSm.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
