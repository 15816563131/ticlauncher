package com.trae.ticlauncher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * 控制中心内容控制器（ColorOS 风格）。
 *
 * 从原来的 {@code QuickSettingsActivity} 抽出来，改成绑定到任意 View 根节点，
 * 这样既能内嵌进启动器做成「跟手下拉浮层」，也能被别的界面复用。
 */
public class QuickPanelController {

    private final Activity mActivity;
    private final Runnable mOnRequestClose;

    private BroadcastReceiver mBatteryReceiver;

    private LinearLayout mToggleAirplane, mToggleDnd, mToggleTheater, mToggleSaver;
    private LinearLayout mToggleBt, mToggleWifi, mToggleFind, mToggleFlash;
    private ImageView mIconAirplane, mIconDnd, mIconTheater, mIconSaver;
    private ImageView mIconBt, mIconWifi, mIconFind, mIconFlash;
    private ImageView mBatteryIcon;
    private TextView mBatteryText;
    private SeekBar mBrightness;

    // 系统无公开状态可读的开关，用本地状态记录
    private boolean mTheaterOn = false;
    private boolean mFlashOn = false;

    public QuickPanelController(Activity activity, View root, Runnable onRequestClose) {
        mActivity = activity;
        mOnRequestClose = onRequestClose;

        mBatteryIcon = root.findViewById(R.id.battery_icon);
        mBatteryText = root.findViewById(R.id.battery_text);

        mToggleAirplane = root.findViewById(R.id.toggle_airplane);
        mToggleDnd = root.findViewById(R.id.toggle_dnd);
        mToggleTheater = root.findViewById(R.id.toggle_theater);
        mToggleSaver = root.findViewById(R.id.toggle_saver);
        mToggleBt = root.findViewById(R.id.toggle_bluetooth);
        mToggleWifi = root.findViewById(R.id.toggle_wifi);
        mToggleFind = root.findViewById(R.id.toggle_findphone);
        mToggleFlash = root.findViewById(R.id.toggle_flashlight);

        mIconAirplane = root.findViewById(R.id.icon_airplane);
        mIconDnd = root.findViewById(R.id.icon_dnd);
        mIconTheater = root.findViewById(R.id.icon_theater);
        mIconSaver = root.findViewById(R.id.icon_saver);
        mIconBt = root.findViewById(R.id.icon_bluetooth);
        mIconWifi = root.findViewById(R.id.icon_wifi);
        mIconFind = root.findViewById(R.id.icon_findphone);
        mIconFlash = root.findViewById(R.id.icon_flashlight);

        // 飞行模式
        mToggleAirplane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !isAirplaneOn();
                // 只写系统设置即可生效：框架用 ContentObserver 监听 Settings.Global.AIRPLANE_MODE_ON。
                // 不能再补发 ACTION_AIRPLANE_MODE_CHANGED —— 它是受保护广播，
                // 普通应用发出去会抛 SecurityException 直接把应用打崩（实测）。
                if (!putGlobal(Settings.Global.AIRPLANE_MODE_ON, on ? 1 : 0)) {
                    toast("飞行模式需要 WRITE_SECURE_SETTINGS");
                }
                // 系统真正执行要几百毫秒，稍等一下再刷新图标
                mToggleAirplane.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 700);
            }
        });

        // 勿扰
        mToggleDnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mode = zenMode();
                int next = (mode == 2) ? 0 : 2; // NO_INTERRUPTIONS <-> OFF
                if (!putGlobal("zen_mode", next)) {
                    toast("勿扰需要 WRITE_SECURE_SETTINGS");
                }
                refresh();
            }
        });

        // 影院模式（系统受保护广播，无公开状态）
        mToggleTheater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.android.clockwork.settings.START_THEATER_MODE");
                i.setPackage("com.google.android.wearable.app");
                boolean ok = sendProtected(i);
                if (ok) {
                    mTheaterOn = !mTheaterOn;
                } else {
                    toast("影院模式需系统权限");
                }
                refresh();
            }
        });

        // 省电
        mToggleSaver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !isPowerSave();
                Intent i = new Intent("com.google.android.wearable.home.action.ENABLE_POWER_SAVE_MODE");
                i.setPackage("com.google.android.wearable.app");
                i.putExtra("enable", on);
                boolean ok = sendProtected(i) || putGlobal("low_power", on ? 1 : 0);
                if (!ok) {
                    toast("省电模式需系统权限");
                }
                refresh();
            }
        });

        // 蓝牙
        mToggleBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                    if (a == null) {
                        toast("无蓝牙适配器");
                    } else if (a.isEnabled()) {
                        //noinspection MissingPermission
                        a.disable();
                    } else {
                        //noinspection MissingPermission
                        a.enable();
                    }
                } catch (Exception e) {
                    toast("蓝牙开关需要 BLUETOOTH_ADMIN");
                }
                mToggleBt.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 800);
            }
        });

        // WLAN
        mToggleWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    WifiManager wm = (WifiManager) mActivity.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    if (wm == null) {
                        toast("无 WLAN");
                    } else {
                        //noinspection deprecation
                        wm.setWifiEnabled(!wm.isWifiEnabled());
                    }
                } catch (Exception e) {
                    toast("WLAN 开关需要 CHANGE_WIFI_STATE");
                }
                mToggleWifi.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 800);
            }
        });

        // 找手机（交给 Wear OS 系统应用）
        mToggleFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!launchByKeyword("com.google.android.wearable.app", "findphone")) {
                    toast("未找到找手机功能");
                }
            }
        });

        // 手电筒（交给 Wear OS 系统应用）
        mToggleFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!launchByKeyword("com.google.android.wearable.app", "flashlight")) {
                    toast("未找到手电筒");
                }
            }
        });

        mBrightness = root.findViewById(R.id.brightness_seek);
        mBrightness.setProgress(currentBrightness());
        mBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                try {
                    Settings.System.putInt(mActivity.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(mActivity.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, Math.max(12, p));
                } catch (Exception e) {
                    // 忽略：无 WRITE_SETTINGS
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        root.findViewById(R.id.action_touchlock).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.android.clockwork.settings.WATCH_TOUCH");
                i.setPackage("com.google.android.wearable.app");
                i.putExtra("enable", false);
                if (!sendProtected(i)) {
                    toast("触控锁需系统权限");
                }
            }
        });

        root.findViewById(R.id.action_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestClose();
                try {
                    mActivity.startActivity(new Intent(mActivity, SettingsActivity.class));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void onResume() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct = scale > 0 && level >= 0 ? level * 100 / scale : -1;
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                mBatteryText.setText(pct >= 0
                        ? String.format(Locale.getDefault(), "%d%%", pct) : "--%");
                mBatteryIcon.setImageResource(batteryIcon(pct, charging));
            }
        };
        mActivity.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mBrightness.setProgress(currentBrightness());
        refresh();
    }

    public void onPause() {
        if (mBatteryReceiver != null) {
            try {
                mActivity.unregisterReceiver(mBatteryReceiver);
            } catch (Exception ignored) {
            }
            mBatteryReceiver = null;
        }
    }

    public void refresh() {
        applyToggle(mToggleAirplane, mIconAirplane, isAirplaneOn());
        applyToggle(mToggleDnd, mIconDnd, zenMode() != 0);
        applyToggle(mToggleSaver, mIconSaver, isPowerSave());
        applyToggle(mToggleTheater, mIconTheater, mTheaterOn);

        boolean btOn = isBtOn();
        mIconBt.setImageResource(btOn
                ? R.drawable.ic_qss_bluetooth_connected : R.drawable.ic_qss_bluetooth_disconnected);
        applyToggle(mToggleBt, mIconBt, btOn);

        boolean wifiOn = isWifiOn();
        mIconWifi.setImageResource(wifiOn && !isOnline()
                ? R.drawable.ic_qss_wifi_not_connected : R.drawable.ic_qss_wifi);
        applyToggle(mToggleWifi, mIconWifi, wifiOn);

        applyToggle(mToggleFind, mIconFind, false);
        applyToggle(mToggleFlash, mIconFlash, mFlashOn);
    }

    private void requestClose() {
        if (mOnRequestClose != null) {
            mOnRequestClose.run();
        }
    }

    // ---------- 状态 ----------

    private int batteryIcon(int pct, boolean charging) {
        if (charging) {
            if (pct >= 90) return R.drawable.ic_battery_100_charging;
            if (pct >= 70) return R.drawable.ic_battery_80_charging;
            if (pct >= 50) return R.drawable.ic_battery_60_charging;
            if (pct >= 30) return R.drawable.ic_battery_40_charging;
            if (pct >= 15) return R.drawable.ic_battery_20_charging;
            return R.drawable.ic_battery_10_charging;
        }
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

    private boolean isAirplaneOn() {
        try {
            return Settings.Global.getInt(mActivity.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private int zenMode() {
        try {
            return Settings.Global.getInt(mActivity.getContentResolver(), "zen_mode");
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isPowerSave() {
        try {
            return Settings.Global.getInt(mActivity.getContentResolver(), "low_power") == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBtOn() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWifiOn() {
        try {
            WifiManager wm = (WifiManager) mActivity.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private int currentBrightness() {
        try {
            int v = Settings.System.getInt(mActivity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            return Math.max(0, Math.min(255, v));
        } catch (Exception e) {
            return 128;
        }
    }

    /** 是否已连上网络（用于 WLAN 开关的「已连接 / 未连接」图标）。 */
    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void applyToggle(LinearLayout box, ImageView icon, boolean on) {
        box.setSelected(on);
        icon.setColorFilter(on ? Color.parseColor("#FFFE7C48")
                : Color.parseColor("#FF99A7CA"), PorterDuff.Mode.SRC_IN);
    }

    // ---------- 工具 ----------

    private boolean putGlobal(String key, int value) {
        try {
            return Settings.Global.putInt(mActivity.getContentResolver(), key, value);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean sendProtected(Intent i) {
        try {
            mActivity.sendBroadcast(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在指定包内按关键字查找并启动 Activity（用于找手机 / 手电筒等系统功能）。 */
    private boolean launchByKeyword(String pkg, String keyword) {
        try {
            PackageManager pm = mActivity.getPackageManager();
            Intent probe = new Intent(Intent.ACTION_MAIN);
            probe.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
            String kw = keyword.toLowerCase(Locale.ROOT);
            for (ResolveInfo ri : list) {
                if (!pkg.equals(ri.activityInfo.packageName)) {
                    continue;
                }
                if (ri.activityInfo.name.toLowerCase(Locale.ROOT).contains(kw)) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.setComponent(new ComponentName(pkg, ri.activityInfo.name));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(i);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void toast(String s) {
        Toast.makeText(mActivity, s, Toast.LENGTH_SHORT).show();
    }
}
