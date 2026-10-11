package com.trae.ticlauncher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;

/**
 * 「自动休眠」调度器。
 *
 * 到设定的开始时间把 WLAN 关掉（记住原来的状态），到结束时间再恢复。
 * 只定两个精确闹钟，平时不驻留进程、不轮询、不占唤醒锁。
 *
 * 关 WLAN 是睡眠期间唯一能安全削掉的东西：
 *  · 睡眠时手表不需要独立联网（与手机的蓝牙连接不受影响，通知照收）；
 *  · 省掉一整夜的 Wi-Fi 保活与后台扫描；
 *  · 健康采集（步数是硬件计数器、心率随时可重采）与系统功能一律不动，醒来即自动恢复。
 *
 * 注意：手表休眠窗口内 WLAN 会被关掉，靠 Wi-Fi 连 adb 会断开，
 * 到结束时间会自动恢复，不需要人工干预。
 */
public class SleepModeReceiver extends BroadcastReceiver {

    private static final String TAG = "SleepMode";

    /** 自己的调度动作，由 AlarmManager 精确触发。 */
    private static final String ACTION_TICK = "com.trae.ticlauncher.action.SLEEP_TICK";

    // 与 PowerSaveActivity 共用同一份偏好
    private static final String PREF = "power_pref";
    private static final String KEY_ENABLED = "sleep_enabled";
    private static final String KEY_START = "sleep_start";
    private static final String KEY_END = "sleep_end";

    /** 本次休眠是否已经把 WLAN 关掉了（防止重复关/漏恢复）。 */
    private static final String KEY_ACTIVE = "sleep_active";
    /** 关 WLAN 之前它是不是开着的。 */
    private static final String KEY_WIFI_BEFORE = "wifi_before_sleep";

    private static final int DEFAULT_START = 22 * 60;
    private static final int DEFAULT_END = 7 * 60;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "tick action=" + (intent == null ? null : intent.getAction()));
        applyAndReschedule(context);
    }

    // ---------- 对外入口 ----------

    /**
     * 重新校正当前状态并安排下一个边界时刻的闹钟。
     * 开关变化、时间变化、桌面启动时都调用一次。
     */
    public static void reschedule(Context context) {
        applyAndReschedule(context);
    }

    private static void applyAndReschedule(Context context) {
        SharedPreferences sp = prefs(context);
        if (!sp.getBoolean(KEY_ENABLED, false)) {
            exitSleep(context);   // 关掉开关时保证 WLAN 一定被恢复
            cancelAlarm(context);
            return;
        }
        long now = System.currentTimeMillis();
        if (inWindow(sp, now)) {
            enterSleep(context);
        } else {
            exitSleep(context);
        }
        long next = nextBoundary(sp, now);
        setAlarm(context, next);
        Log.d(TAG, "enabled, next boundary in " + ((next - now) / 60000L) + " min");
    }

    // ---------- 时间计算 ----------

    private static int startMin(SharedPreferences sp) {
        return sp.getInt(KEY_START, DEFAULT_START);
    }

    private static int endMin(SharedPreferences sp) {
        return sp.getInt(KEY_END, DEFAULT_END);
    }

    /** 现在是否处在休眠窗口内（支持跨零点，例如 22:00 → 07:00）。 */
    private static boolean inWindow(SharedPreferences sp, long now) {
        int cur = minutesOfDay(now);
        int s = startMin(sp);
        int e = endMin(sp);
        if (s == e) {
            return false;
        }
        return s < e ? (cur >= s && cur < e) : (cur >= s || cur < e);
    }

    /** 下一个「开始 / 结束」时刻（严格晚于 now）。 */
    private static long nextBoundary(SharedPreferences sp, long now) {
        int s = startMin(sp);
        int e = endMin(sp);
        long best = 0L;
        for (int day = 0; day <= 2; day++) {
            for (int m : new int[]{s, e}) {
                long t = atMinute(now, day, m);
                if (t > now && (best == 0L || t < best)) {
                    best = t;
                }
            }
        }
        // 理论上走不到这里，兜底 6 小时后再算一次
        return best == 0L ? now + 6 * 60 * 60 * 1000L : best;
    }

    private static int minutesOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** now 之后第 day 天的 min 分对应的时间戳（秒/毫秒归零）。 */
    private static long atMinute(long now, int day, int min) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.DAY_OF_YEAR, day);
        c.set(Calendar.HOUR_OF_DAY, min / 60);
        c.set(Calendar.MINUTE, min % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ---------- 闹钟 ----------

    private static PendingIntent pendingIntent(Context context) {
        Intent i = new Intent(context, SleepModeReceiver.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void setAlarm(Context context, long at) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        // allow-while-idle 版本：打盹状态下也能准时把 WLAN 切回来，不会卡一整夜
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context));
    }

    private static void cancelAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(pendingIntent(context));
    }

    // ---------- WLAN 开关 ----------

    private static void enterSleep(Context context) {
        SharedPreferences sp = prefs(context);
        if (sp.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        boolean before = isWifiOn(context);
        sp.edit().putBoolean(KEY_ACTIVE, true).putBoolean(KEY_WIFI_BEFORE, before).apply();
        if (before) {
            setWifi(context, false);
            Log.d(TAG, "sleep start: WLAN off");
        } else {
            Log.d(TAG, "sleep start: WLAN already off");
        }
    }

    private static void exitSleep(Context context) {
        SharedPreferences sp = prefs(context);
        if (!sp.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        boolean restore = sp.getBoolean(KEY_WIFI_BEFORE, false);
        sp.edit().putBoolean(KEY_ACTIVE, false).apply();
        if (restore) {
            setWifi(context, true);
            Log.d(TAG, "sleep end: WLAN restored");
        }
    }

    private static boolean isWifiOn(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private static void setWifi(Context context, boolean on) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.setWifiEnabled(on)) {
                return;
            }
        } catch (Exception ignored) {
        }
        // 个别 ROM 会拦 setWifiEnabled，退回直接写系统开关
        try {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.WIFI_ON, on ? 1 : 0);
        } catch (Exception ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
