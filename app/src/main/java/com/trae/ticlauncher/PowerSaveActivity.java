package com.trae.ticlauncher;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Locale;

/**
 * ColorOS 手表：电池 / 省电模式（复刻 HeyPowerManager）。
 *
 * 省电要点：
 *  1) 只在可见时注册电量广播，onPause 立刻注销，避免常驻唤醒；
 *  2) 不持有任何传感器 / 定时器；
 *  3) 省电开关直接写 Settings.Global "low_power"，由系统统一降频。
 */
public class PowerSaveActivity extends Activity {

    private static final String PREF = "power_pref";
    private static final String KEY_SLEEP_ENABLED = "sleep_enabled";
    private static final String KEY_SLEEP_START = "sleep_start";
    private static final String KEY_SLEEP_END = "sleep_end";

    private TextView mLevel, mTip, mSaverState, mStartTime, mEndTime;
    private ProgressBar mProgress;
    private FrameLayout mSaverSwitch, mSleepSwitch;
    private View mSaverThumb, mSleepThumb;

    private BroadcastReceiver mBatteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_save);

        mLevel = findViewById(R.id.ps_level);
        mTip = findViewById(R.id.ps_tip);
        mProgress = findViewById(R.id.ps_progress);
        mSaverState = findViewById(R.id.ps_saver_state);
        mStartTime = findViewById(R.id.ps_start_time);
        mEndTime = findViewById(R.id.ps_end_time);
        mSaverSwitch = findViewById(R.id.ps_switch);
        mSleepSwitch = findViewById(R.id.ps_sleep_switch);
        mSaverThumb = findViewById(R.id.ps_switch_thumb);
        mSleepThumb = findViewById(R.id.ps_sleep_thumb);

        // 省电模式
        mSaverSwitch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !isPowerSave();
                if (!setPowerSave(on)) {
                    toast("需要 WRITE_SECURE_SETTINGS 权限");
                }
                refreshSaver();
            }
        });

        // 自动休眠
        mSleepSwitch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !isSleepEnabled();
                prefs().edit().putBoolean(KEY_SLEEP_ENABLED, on).apply();
                refreshSleep();
            }
        });

        // 开始 / 结束时间
        findViewById(R.id.ps_start_row).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickTime(true); }
        });
        findViewById(R.id.ps_end_row).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickTime(false); }
        });

        // 轻智能模式
        findViewById(R.id.ps_smart_row).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(PowerSaveActivity.this, SmartModeActivity.class));
            }
        });

        // 电池用量
        findViewById(R.id.ps_usage_row).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPowerUsage(); }
        });

        refreshSaver();
        refreshSleep();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBatteryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level < 0 || scale <= 0) return;
                int pct = level * 100 / scale;
                renderBattery(pct);
            }
        };
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        refreshSaver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBatteryReceiver != null) {
            try { unregisterReceiver(mBatteryReceiver); } catch (Exception ignored) {}
            mBatteryReceiver = null;
        }
    }

    private void renderBattery(int pct) {
        mLevel.setText(pct + "%");
        mProgress.setProgress(pct);
        // 电量越低，颜色越警示
        int color = pct >= 50 ? 0xFF41D97A : (pct >= 20 ? 0xFFFE7C48 : 0xFFED5844);
        mLevel.setTextColor(color);
        mTip.setText(pct >= 50 ? "预计可用 1 天以上"
                : (pct >= 20 ? "预计可用约 1 天" : "电量偏低，建议开启省电模式"));
    }

    // ---------- 省电模式 ----------

    private boolean isPowerSave() {
        try {
            return Settings.Global.getInt(getContentResolver(), "low_power") == 1;
        } catch (Exception e) {
            return prefs().getBoolean("local_low_power", false);
        }
    }

    private boolean setPowerSave(boolean on) {
        boolean ok = false;
        try {
            ok = Settings.Global.putInt(getContentResolver(), "low_power", on ? 1 : 0);
        } catch (Exception ignored) {}
        if (!ok) {
            prefs().edit().putBoolean("local_low_power", on).apply();
        }
        // 同步通知系统省电服务
        try {
            Intent i = new Intent("com.google.android.wearable.home.action.ENABLE_POWER_SAVE_MODE");
            i.setPackage("com.google.android.wearable.app");
            i.putExtra("enable", on);
            sendBroadcast(i);
        } catch (Exception ignored) {}
        return ok;
    }

    private void refreshSaver() {
        boolean on = isPowerSave();
        bindSwitch(mSaverSwitch, mSaverThumb, on);
        mSaverState.setText(on ? "已开启 · 限制后台活动" : "已关闭");
        mSaverState.setTextColor(on ? 0xFF41D97A : 0xFF888888);
    }

    // ---------- 自动休眠 ----------

    private boolean isSleepEnabled() {
        return prefs().getBoolean(KEY_SLEEP_ENABLED, false);
    }

    private void refreshSleep() {
        boolean on = isSleepEnabled();
        bindSwitch(mSleepSwitch, mSleepThumb, on);
        mStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                prefs().getInt(KEY_SLEEP_START, 22 * 60) / 60, prefs().getInt(KEY_SLEEP_START, 22 * 60) % 60));
        mEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                prefs().getInt(KEY_SLEEP_END, 7 * 60) / 60, prefs().getInt(KEY_SLEEP_END, 7 * 60) % 60));
    }

    private void pickTime(final boolean start) {
        int cur = prefs().getInt(start ? KEY_SLEEP_START : KEY_SLEEP_END, start ? 22 * 60 : 7 * 60);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker view, int hour, int minute) {
                prefs().edit()
                        .putInt(start ? KEY_SLEEP_START : KEY_SLEEP_END, hour * 60 + minute)
                        .apply();
                refreshSleep();
            }
        }, cur / 60, cur % 60, true).show();
    }

    // ---------- 工具 ----------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    /** 用位移动画切换自绘开关（纯 XML，无 Canvas）。 */
    private void bindSwitch(FrameLayout track, View thumb, boolean on) {
        track.setSelected(on);
        float travel = 20f * getResources().getDisplayMetrics().density;
        thumb.animate()
                .setDuration(160)
                .translationX(on ? travel : 0f)
                .start();
    }

    private void openPowerUsage() {
        try {
            Intent i = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
            startActivity(i);
        } catch (Exception e) {
            toast("无电池用量界面");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
