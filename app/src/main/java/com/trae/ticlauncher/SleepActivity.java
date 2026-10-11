package com.trae.ticlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;


/**
 * ColorOS 手表：睡眠应用（复刻 HeySleep）。
 *
 * 睡眠数据需要底层 HealthService 提供，我们这里先展示占位数据。
 * 用户若有真实数据，可接入 HealthService 的 ContentProvider。
 */
public class SleepActivity extends Activity {

    private static final String PREF = "sleep_pref";
    private static final String KEY_TOTAL = "total_min";
    private static final String KEY_LIGHT = "light";
    private static final String KEY_DEEP = "deep";
    private static final String KEY_REM = "rem";
    private static final String KEY_SCORE = "score";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep);

        findViewById(R.id.sleep_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        loadData();
    }

    private void loadData() {
        android.content.SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        int total = sp.getInt(KEY_TOTAL, -1);
        int light = sp.getInt(KEY_LIGHT, -1);
        int deep = sp.getInt(KEY_DEEP, -1);
        int rem = sp.getInt(KEY_REM, -1);
        int score = sp.getInt(KEY_SCORE, -1);

        TextView tvTotal = findViewById(R.id.sleep_total);
        TextView tvLight = findViewById(R.id.sleep_light);
        TextView tvDeep = findViewById(R.id.sleep_deep);
        TextView tvRem = findViewById(R.id.sleep_rem);
        TextView tvScore = findViewById(R.id.sleep_score);

        if (total > 0) {
            float h = total / 60f;
            tvTotal.setText(String.format("%.1f", h));
        } else {
            tvTotal.setText("--");
        }

        tvLight.setText(light >= 0 ? light + " 分钟" : "-- 分钟");
        tvDeep.setText(deep >= 0 ? deep + " 分钟" : "-- 分钟");
        tvRem.setText(rem >= 0 ? rem + " 分钟" : "-- 分钟");

        if (score > 0) {
            tvScore.setText(String.valueOf(score));
        } else {
            tvScore.setText("--");
        }
    }
}
