package com.trae.ticlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ColorOS 手表：日历 / 日程（复刻 HeyCalendar 的日程视图）。
 *
 * 数据源：系统 CalendarProvider（CalendarContract）。
 * 省电要点：仅在 onResume 查询一次当天日程，无轮询、无后台服务。
 */
public class CalendarActivity extends Activity {

    private static final int REQ_CAL = 301;

    static class Event {
        String title;
        long begin;
        long end;
        int color;
    }

    private final List<Event> mEvents = new ArrayList<>();
    private ListView mList;
    private TextView mEmpty, mDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        mList = findViewById(R.id.cal_list);
        mEmpty = findViewById(R.id.cal_empty);
        mDate = findViewById(R.id.cal_date);

        Calendar c = Calendar.getInstance();
        String[] week = {"日", "一", "二", "三", "四", "五", "六"};
        mDate.setText(String.format(Locale.getDefault(), "%d月%d日 周%s",
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                week[c.get(Calendar.DAY_OF_WEEK) - 1]));

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAL) loadEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    private void loadEvents() {
        mEvents.clear();
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            render();
            return;
        }
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, start.getTimeInMillis());
        ContentUris.appendId(builder, end.getTimeInMillis());

        String[] proj = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.DISPLAY_COLOR
        };
        Cursor cur = null;
        try {
            cur = getContentResolver().query(builder.build(), proj, null, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (cur != null) {
                while (cur.moveToNext()) {
                    Event e = new Event();
                    e.title = cur.getString(0);
                    e.begin = cur.getLong(1);
                    e.end = cur.getLong(2);
                    e.color = cur.getInt(3);
                    if (e.title == null || e.title.trim().isEmpty()) e.title = "（无标题）";
                    mEvents.add(e);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        render();
    }

    private void render() {
        if (mEvents.isEmpty()) {
            mList.setVisibility(View.GONE);
            mEmpty.setVisibility(View.VISIBLE);
            return;
        }
        mEmpty.setVisibility(View.GONE);
        mList.setVisibility(View.VISIBLE);
        mList.setAdapter(new EventAdapter());
    }

    private static String fmt(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private class EventAdapter extends BaseAdapter {
        private final LayoutInflater mInf;

        EventAdapter() {
            mInf = LayoutInflater.from(CalendarActivity.this);
        }

        @Override public int getCount() { return mEvents.size(); }
        @Override public Object getItem(int p) { return mEvents.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInf.inflate(R.layout.item_calendar_event, parent, false);
            }
            Event e = mEvents.get(position);
            ((TextView) convertView.findViewById(R.id.ev_title)).setText(e.title);
            ((TextView) convertView.findViewById(R.id.ev_time))
                    .setText(fmt(e.begin) + " - " + fmt(e.end));
            convertView.findViewById(R.id.ev_bar).setBackgroundColor(
                    (e.color & 0x00FFFFFF) == 0 ? 0xFFFE7C48 : 0xFF000000 | (e.color & 0x00FFFFFF));
            return convertView;
        }
    }
}
