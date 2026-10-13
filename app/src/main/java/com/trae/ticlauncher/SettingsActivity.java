package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    public static final String PREFS = "launcher";
    public static final String KEY_LIST_STYLE = "applist_style";
    /** 心率页自动测量间隔（分钟）：0=连续，1/5/10=按间隔突发，-1=关闭。 */
    public static final String KEY_HR_INTERVAL = "hr_auto_interval";
    /** 负一屏心率刷新（秒）：0=连续，30/60=按间隔突发，-1=关闭。 */
    public static final String KEY_HR_PANEL_INTERVAL = "hr_panel_interval";

    private TextView mListStyleText;
    private TextView mHrIntervalText;
    private TextView mHrPanelText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 外观 → 表盘
        findViewById(R.id.btn_watchface).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, WatchFaceActivity.class));
            }
        });

        // 外观 → 应用视图（官方「应用视图」弹窗：行星 / 宫格 / 列表）
        mListStyleText = findViewById(R.id.list_style_text);
        updateListStyleButton();
        findViewById(R.id.btn_list_style).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AppViewStyleActivity.class));
            }
        });

        // 设为默认启动器
        findViewById(R.id.btn_default).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openHomeSettings(); }
        });

        // 电池 / 省电与续航
        findViewById(R.id.btn_power).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PowerSaveActivity.class));
            }
        });

        // 控制中心
        findViewById(R.id.btn_quick).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(SettingsActivity.this, MainActivity.class);
                    i.putExtra(MainActivity.EXTRA_SHOW_QUICK, true);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    finish();
                } catch (Exception ignored) {}
            }
        });

        // 退回系统桌面
        findViewById(R.id.btn_stock_home).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!HomeHelper.openStockHome(SettingsActivity.this)) {
                    Toast.makeText(SettingsActivity.this, "未找到系统桌面", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 系统设置（亮度/蓝牙/WLAN）
        findViewById(R.id.btn_system_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSystemSettings(); }
        });

        // 健康 → 心率自动测量间隔（点击循环：连续 / 1 分钟 / 5 分钟 / 10 分钟 / 关闭）
        mHrIntervalText = findViewById(R.id.hr_interval_text);
        updateHrIntervalText();
        findViewById(R.id.btn_hr_interval).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getInt(KEY_HR_INTERVAL, 0);
                int next;
                switch (cur) {
                    case 0: next = 1; break;
                    case 1: next = 5; break;
                    case 5: next = 10; break;
                    case 10: next = -1; break;
                    default: next = 0; break;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(KEY_HR_INTERVAL, next).apply();
                updateHrIntervalText();
                Toast.makeText(SettingsActivity.this, "心率自动测量：" + hrIntervalLabel(next),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 健康 → 负一屏心率刷新（点击循环：连续 / 30 秒 / 1 分钟 / 关闭）
        mHrPanelText = findViewById(R.id.hr_panel_text);
        updateHrPanelText();
        findViewById(R.id.btn_hr_panel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getInt(KEY_HR_PANEL_INTERVAL, 0);
                int next;
                switch (cur) {
                    case 0: next = 30; break;
                    case 30: next = 60; break;
                    case 60: next = -1; break;
                    default: next = 0; break;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(KEY_HR_PANEL_INTERVAL, next).apply();
                updateHrPanelText();
                Toast.makeText(SettingsActivity.this, "负一屏心率：" + hrPanelLabel(next),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String hrIntervalLabel(int min) {
        if (min < 0) return "关闭";
        if (min == 0) return "连续";
        return min + " 分钟";
    }

    private static String hrPanelLabel(int sec) {
        if (sec < 0) return "关闭";
        if (sec == 0) return "连续";
        if (sec < 60) return sec + " 秒";
        return (sec / 60) + " 分钟";
    }

    private void updateHrIntervalText() {
        if (mHrIntervalText != null) {
            int cur = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HR_INTERVAL, 0);
            mHrIntervalText.setText(hrIntervalLabel(cur));
        }
    }

    private void updateHrPanelText() {
        if (mHrPanelText != null) {
            int cur = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HR_PANEL_INTERVAL, 0);
            mHrPanelText.setText(hrPanelLabel(cur));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateListStyleButton();
        updateHrIntervalText();
        updateHrPanelText();
    }

    private int currentStyle() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_LIST_STYLE, AppListAdapter.MODE_LIST);
    }

    private void updateListStyleButton() {
        if (mListStyleText != null) {
            mListStyleText.setText(AppViewStyleActivity.styleName(currentStyle()));
        }
    }

    private void openHomeSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            openSystemSettings();
        }
    }

    private void openSystemSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }
}
