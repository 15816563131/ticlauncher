package com.trae.ticlauncher;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * ColorOS 手表：时钟（复刻 HeyClock：闹钟 / 计时器 / 秒表）。
 *
 * 委托系统时钟应用完成新建与查看，本页只做入口与「下一个闹钟」展示。
 * 省电要点：仅在 onResume 读取一次下一个闹钟，无任何后台轮询。
 */
public class ClockActivity extends Activity {

    private TextView mNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clock);

        mNext = findViewById(R.id.ck_next);

        findViewById(R.id.ck_alarm).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newAlarm(); }
        });
        findViewById(R.id.ck_timer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(new Intent(AlarmClock.ACTION_SET_TIMER), "无计时器界面");
            }
        });
        findViewById(R.id.ck_stopwatch).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(new Intent(AlarmClock.ACTION_SHOW_TIMERS), "无秒表界面");
            }
        });
        findViewById(R.id.ck_show_alarms).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "无闹钟界面");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNextAlarm();
    }

    private void refreshNextAlarm() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am != null ? am.getNextAlarmClock() : null;
            if (info == null) {
                mNext.setText("未设置闹钟");
                return;
            }
            Calendar now = Calendar.getInstance();
            Calendar at = Calendar.getInstance();
            at.setTimeInMillis(info.getTriggerTime());

            String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(info.getTriggerTime()));
            String day;
            if (isSameDay(now, at)) day = "";
            else if (isTomorrow(now, at)) day = " 明天";
            else day = String.format(Locale.getDefault(), " %d月%d日",
                    at.get(Calendar.MONTH) + 1, at.get(Calendar.DAY_OF_MONTH));
            mNext.setText(time + day);
        } catch (Exception e) {
            mNext.setText("未设置闹钟");
        }
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isTomorrow(Calendar now, Calendar at) {
        Calendar t = (Calendar) now.clone();
        t.add(Calendar.DAY_OF_YEAR, 1);
        return isSameDay(t, at);
    }

    private void newAlarm() {
        Calendar c = Calendar.getInstance();
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
        i.putExtra(AlarmClock.EXTRA_HOUR, (c.get(Calendar.HOUR_OF_DAY) + 1) % 24);
        i.putExtra(AlarmClock.EXTRA_MINUTES, 0);
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        launch(i, "无闹钟界面");
    }

    private void launch(Intent i, String err) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        }
    }
}
