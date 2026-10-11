package com.trae.ticlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.Calendar;

/**
 * 读取今日步数。数据源为 Android 标准 {@link Sensor#TYPE_STEP_COUNTER}。
 *
 * 首次收到传感器值时记为「今日基准」，之后每日零点自动更新，
 * 从而获得真正的「今日」步数。
 */
public final class StepSource {

    /** 每日步数目标（ColorOS 默认 8000）。 */
    public static final int DEFAULT_GOAL = 8000;

    private static final String PREFS = "steps";
    private static final String KEY_BASELINE = "baseline";
    private static final String KEY_BASELINE_DAY = "baseline_day";

    public int steps = -1;
    public float distance = -1f;
    public int goal = -1;
    public int calorie = -1;

    public boolean valid() {
        return steps >= 0;
    }

    public static StepSource compute(Context context, float totalSinceBoot) {
        StepSource d = new StepSource();
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.YEAR) * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int savedDay = sp.getInt(KEY_BASELINE_DAY, -1);
        float baseline;
        if (savedDay != today) {
            baseline = totalSinceBoot;
            sp.edit().putFloat(KEY_BASELINE, baseline)
                    .putInt(KEY_BASELINE_DAY, today).apply();
        } else {
            baseline = sp.getFloat(KEY_BASELINE, totalSinceBoot);
        }

        int todaySteps = Math.max(0, Math.round(totalSinceBoot - baseline));
        d.steps = todaySteps;
        d.goal = DEFAULT_GOAL;
        d.distance = todaySteps * 0.7f;
        d.calorie = Math.round(todaySteps * 0.04f);
        return d;
    }

    /** 清理旧数据（重启等场景）——把当前值记为新基准。 */
    public static void resetBaseline(Context context, float currentTotal) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.YEAR) * 10000
                + (cal.get(Calendar.MONTH) + 1) * 100
                + cal.get(Calendar.DAY_OF_MONTH);
        sp.edit().putFloat(KEY_BASELINE, currentTotal)
                .putInt(KEY_BASELINE_DAY, today).apply();
    }

    /** 在某个时间点已经读过一次步数（UI 线程快速读），返回是否读到值。 */
    public static boolean isSensorAvailable(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return false;
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        return s != null;
    }
}
