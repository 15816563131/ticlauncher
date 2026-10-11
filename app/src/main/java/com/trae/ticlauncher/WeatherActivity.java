package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * ColorOS 手表：天气应用（复刻 HeyWeather）。
 *
 * TicWatch 没有系统天气服务，这里做：
 *   1) 启动时从 SharedPreferences 读上次缓存的天气；
 *   2) 点击刷新 → 模拟请求 0.8s 后随机给一个合理温度区间。
 * 后续可接 OpenWeatherMap API。
 */
public class WeatherActivity extends Activity {

    private static final String PREF = "weather_pref";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_DESC = "desc";
    private static final String KEY_ICON = "icon";
    private static final String KEY_UPDATE = "update";

    private static final String[] DESCS = {"晴", "多云", "阴", "小雨", "雷阵雨", "霾"};
    private static final int[] ICONS = {
            R.drawable.ic_app_weather,
            R.drawable.ic_app_weather_g0,
            R.drawable.ic_app_weather_g1,
            R.drawable.ic_app_weather_g2,
            R.drawable.ic_app_weather_g3,
            R.drawable.ic_app_weather
    };

    private TextView mTvCity, mTvTemp, mTvDesc, mTvRange, mTvUpdate;
    private ImageView mIvIcon, mIvRefresh;
    private View mRefreshBtn;

    private final Random mRnd = new Random();
    private boolean mRefreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        mTvCity = findViewById(R.id.w_city);
        mTvTemp = findViewById(R.id.w_temp);
        mTvDesc = findViewById(R.id.w_desc);
        mTvRange = findViewById(R.id.w_range);
        mTvUpdate = findViewById(R.id.w_update);
        mIvIcon = findViewById(R.id.w_icon);
        mIvRefresh = findViewById(R.id.w_refresh_icon);
        mRefreshBtn = findViewById(R.id.w_refresh);

        findViewById(R.id.w_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        mRefreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });

        loadCached();
    }

    private void loadCached() {
        SharedPreferences sp = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int temp = sp.getInt(KEY_TEMP, 24);
        int high = sp.getInt(KEY_HIGH, 28);
        int low = sp.getInt(KEY_LOW, 18);
        String desc = sp.getString(KEY_DESC, "晴");
        int icon = sp.getInt(KEY_ICON, R.drawable.ic_app_weather);
        long update = sp.getLong(KEY_UPDATE, System.currentTimeMillis());

        render(temp, high, low, desc, icon, update);
    }

    private void refresh() {
        if (mRefreshing) return;
        mRefreshing = true;

        // 旋转刷新图标
        mIvRefresh.animate()
                .setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .rotationBy(360f)
                .start();

        // 0.8s 后给新数据
        mRefreshBtn.postDelayed(new Runnable() {
            @Override public void run() {
                int descIdx = mRnd.nextInt(DESCS.length);
                int temp = 18 + mRnd.nextInt(15);       // 18~32
                int high = temp + 2 + mRnd.nextInt(4);  // 比当前高 2~5
                int low = Math.max(0, temp - 5 - mRnd.nextInt(5));
                String desc = DESCS[descIdx];
                int icon = ICONS[descIdx];
                long now = System.currentTimeMillis();

                // 存缓存
                getSharedPreferences(PREF, MODE_PRIVATE).edit()
                        .putInt(KEY_TEMP, temp)
                        .putInt(KEY_HIGH, high)
                        .putInt(KEY_LOW, low)
                        .putString(KEY_DESC, desc)
                        .putInt(KEY_ICON, icon)
                        .putLong(KEY_UPDATE, now)
                        .apply();

                render(temp, high, low, desc, icon, now);
                mRefreshing = false;
            }
        }, 800);
    }

    private void render(int temp, int high, int low, String desc, int icon, long updateTime) {
        mTvTemp.setText(temp + "°");
        mTvDesc.setText(desc);
        mTvRange.setText(low + "° ~ " + high + "°");
        mIvIcon.setImageResource(icon);

        String when;
        long diff = System.currentTimeMillis() - updateTime;
        if (diff < 60_000) when = "刚刚";
        else if (diff < 3_600_000) when = (diff / 60_000) + " 分钟前";
        else if (diff < 86_400_000) when = (diff / 3_600_000) + " 小时前";
        else when = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(updateTime));
        mTvUpdate.setText("上次更新 " + when);

        // 温度变化时做一个微动画
        mTvTemp.setAlpha(0.3f);
        mTvTemp.setScaleX(0.9f);
        mTvTemp.setScaleY(0.9f);
        mTvTemp.animate()
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .alpha(1f).scaleX(1f).scaleY(1f)
                .start();
    }
}
