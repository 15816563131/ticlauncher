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

    private TextView mListStyleText;

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
    }

    private int currentStyle() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_LIST_STYLE, AppListAdapter.MODE_LIST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从「应用视图」弹窗返回后刷新当前视图名
        updateListStyleButton();
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
