package com.trae.ticlauncher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;

/**
 * 「自动休眠」调度器。
 *
 * 到设定的开始时间断网，到结束时间自动恢复。只定两个精确闹钟，
 * 平时不驻留进程、不轮询、不占唤醒锁。
 *
 * 为什么用飞行模式而不是直接关 Wi-Fi：
 *   实测（`adb logcat -s WifiService:WearWifiMediator`）应用调
 *   {@code WifiManager.setWifiEnabled(false)} 是**生效**的，Wi-Fi 确实被关掉，
 *   但约 0.5 秒后系统自己的 Wear OS 策略层会把 Wi-Fi 拉回来：
 *
 *       WearWifiMediator: Wifi is OFF but mWifiDesired is TRUE. Updating state...
 *       WearWifiMediator: setWifiDecision: true; reason: ON_NETWORK_REQUEST
 *       WifiService: setWifiEnabled: true   (package=android uid=1000)
 *
 *   WearWifiMediator 认为「手表必须有网络」，任何应用级的关闭都会被它撤销。
 *   只有飞行模式是它能认的机制。所以这里切飞行模式，然后**把蓝牙再开回来**，
 *   这样网络（Wi-Fi / 蜂窝）断掉省电，但和手机的通知链路不受影响。
 *
 * 注意：睡眠窗口内 Wi-Fi 会被切断，靠 Wi-Fi 连 adb 会断开，
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

    /** 本次休眠是否已经断网（防止重复执行 / 漏恢复）。 */
    private static final String KEY_ACTIVE = "sleep_active";
    /** 进入休眠前飞行模式本来就是开着的吗。 */
    private static final String KEY_AIR_BEFORE = "air_before_sleep";
    /** 进入休眠前蓝牙是开着的吗。 */
    private static final String KEY_BT_BEFORE = "bt_before_sleep";

    private static final int DEFAULT_START = 22 * 60;
    private static final int DEFAULT_END = 7 * 60;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "tick action=" + (intent == null ? null : intent.getAction()));
        // 用 goAsync 让进程在延迟补开蓝牙期间不被回收（最长约 10s 配额）
        final PendingResult result = goAsync();
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    applyAndReschedule(context.getApplicationContext());
                } finally {
                    result.finish();
                }
            }
        }, 0);
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
            exitSleep(context);   // 关掉开关时保证网络一定被恢复
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
        // allow-while-idle 版本：打盹状态下也能准时把网络切回来，不会卡一整夜
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context));
    }

    private static void cancelAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(pendingIntent(context));
    }

    // ---------- 断网 / 恢复 ----------

    private static void enterSleep(Context context) {
        SharedPreferences sp = prefs(context);
        if (sp.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        sp.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_AIR_BEFORE, isAirplaneOn(context))
                .putBoolean(KEY_BT_BEFORE, isBtOn(context))
                .apply();
        setAirplane(context, true);
        Log.d(TAG, "sleep start: airplane on (network cut)");
        keepBtOn(context);
    }

    private static void exitSleep(Context context) {
        SharedPreferences sp = prefs(context);
        if (!sp.getBoolean(KEY_ACTIVE, false)) {
            return;
        }
        boolean airBefore = sp.getBoolean(KEY_AIR_BEFORE, false);
        boolean btBefore = sp.getBoolean(KEY_BT_BEFORE, true);
        sp.edit().putBoolean(KEY_ACTIVE, false).apply();
        if (!airBefore) {
            setAirplane(context, false);
            Log.d(TAG, "sleep end: airplane off (network restored)");
        }
        if (btBefore) {
            scheduleBtOn(context, 800L);
        }
    }

    /**
     * 飞行模式打开后系统会顺手把蓝牙也关掉，隔一会儿补开回来，
     * 保证和手机的通知链路不因为省电而断掉。
     */
    private static void keepBtOn(Context context) {
        scheduleBtOn(context, 1200L);
        scheduleBtOn(context, 3500L);
    }

    private static void scheduleBtOn(final Context context, long delayMs) {
        final Context app = context.getApplicationContext();
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (prefs(app).getBoolean(KEY_ACTIVE, false)) {
                    setBt(app, true);
                }
            }
        }, delayMs);
    }

    private static boolean isAirplaneOn(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 切飞行模式：写系统设置 + 广播，系统收到后才会真正执行。 */
    private static void setAirplane(Context context, boolean on) {
        try {
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, on ? 1 : 0);
            Intent i = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            i.putExtra("state", on);
            context.sendBroadcast(i);
        } catch (Exception e) {
            Log.w(TAG, "setAirplane failed", e);
        }
    }

    private static boolean isBtOn(Context context) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private static void setBt(Context context, boolean on) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) {
                return;
            }
            if (on == a.isEnabled()) {
                return;
            }
            if (on) {
                //noinspection MissingPermission
                a.enable();
            } else {
                //noinspection MissingPermission
                a.disable();
            }
            Log.d(TAG, "bt -> " + on);
        } catch (Exception e) {
            Log.w(TAG, "setBt failed", e);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
