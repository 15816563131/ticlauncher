package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * ColorOS 手表：运动结束页（复刻 HeySports activity_sport_end + record_main_bottom_view.xml）。
 *
 * 展示本次汇总（距离 / 时长 / 心率）+ 官方 ic_record_bottom 图标
 * + 官方提示「更多详情请前往手机 OHealth 查看」+「完成」。
 */
public class SportEndActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_end);

        Intent in = getIntent();
        String name = in.getStringExtra(SportRecordActivity.EXTRA_SPORT_NAME);
        float km = in.getFloatExtra(SportRecordActivity.EXTRA_DISTANCE, 0f);
        long sec = in.getLongExtra(SportRecordActivity.EXTRA_DURATION, 0L);
        int heart = in.getIntExtra(SportRecordActivity.EXTRA_HEART, 0);

        TextView tvTitle = findViewById(R.id.end_title);
        if (name != null && name.length() > 0) {
            tvTitle.setText(name + "已结束");
        }

        ((TextView) findViewById(R.id.end_distance))
                .setText(String.format(Locale.getDefault(), "%.2f", km));
        ((TextView) findViewById(R.id.end_duration))
                .setText(String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60));
        ((TextView) findViewById(R.id.end_heart))
                .setText(heart > 0 ? String.valueOf(heart) : "--");

        findViewById(R.id.end_finish).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backToSportList(); }
        });
    }

    @Override
    public void onBackPressed() {
        backToSportList();
    }

    /** 回到运动列表，清掉栈中的准备页/记录页。 */
    private void backToSportList() {
        Intent i = new Intent(this, SportActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
