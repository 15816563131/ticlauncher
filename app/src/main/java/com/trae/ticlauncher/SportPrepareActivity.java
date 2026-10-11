package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

/**
 * ColorOS 手表：运动准备页（复刻 HeySports 官方准备页）。
 *
 * 官方结构见 res/layout/sport_prepare_start_common_layout.xml：
 *   运动名（顶部 14dp / 左右 48dp）+「开始」按钮（marginTop 78dp / 高 66dp / 圆角 60dp）。
 * 点击「开始」后进入官方 SportCountDownView 的 3 秒倒计时（countDownCount=3，
 * 数字颜色 sport_color #ff9cff1f），倒计时结束自动进入运动记录页。
 */
public class SportPrepareActivity extends Activity {

    public static final String EXTRA_SPORT_NAME = "sport_name";

    private static final int COUNT_FROM = 3;

    private TextView mTvType, mBtnStart, mTvCountDown;
    private String mSportName;
    private boolean mCounting;
    private int mCount = COUNT_FROM;

    private final Handler mHandler = new Handler();

    /** 每秒一跳的倒计时（官方 SportCountDownView 的 3-2-1）。 */
    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (mCount <= 0) {
                Intent i = new Intent(SportPrepareActivity.this, SportRecordActivity.class);
                i.putExtra(SportRecordActivity.EXTRA_SPORT_NAME, mSportName);
                startActivity(i);
                finish();
                return;
            }
            mTvCountDown.setText(String.valueOf(mCount));
            // 缩放 + 淡入，模拟官方倒计时数字的冲击感（纯 View 动画，不自绘）
            mTvCountDown.setScaleX(1.6f);
            mTvCountDown.setScaleY(1.6f);
            mTvCountDown.setAlpha(0f);
            mTvCountDown.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).start();
            mCount--;
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_prepare);

        mSportName = getIntent().getStringExtra(EXTRA_SPORT_NAME);
        if (mSportName == null || mSportName.length() == 0) {
            mSportName = "运动";
        }

        mTvType = findViewById(R.id.prep_sport_type);
        mBtnStart = findViewById(R.id.prep_btn_start);
        mTvCountDown = findViewById(R.id.prep_count_down);

        mTvType.setText(mSportName);

        findViewById(R.id.prep_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!mCounting) finish();
            }
        });
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startCountDown();
            }
        });
    }

    private void startCountDown() {
        if (mCounting) return;
        mCounting = true;
        mBtnStart.setVisibility(View.GONE);
        mTvType.setVisibility(View.GONE);
        mTvCountDown.setVisibility(View.VISIBLE);
        mCount = COUNT_FROM;
        mTick.run();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mTick);
    }
}
