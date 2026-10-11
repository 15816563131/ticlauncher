package com.trae.ticlauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import java.util.Locale;

/**
 * ColorOS 手表：呼吸放松（复刻 HeyRelax 的呼吸训练）。
 *
 * 省电要点：呼吸动画与倒计时定时器只在「训练进行中」存在，
 * onPause / 停止时立即 cancel + removeCallbacks，绝不后台空转。
 */
public class RelaxActivity extends Activity {

    private static final int[] DURATIONS_MIN = {1, 2, 3};

    private View mCircle;
    private TextView mHint, mRemain, mStart;
    private TextView[] mChips;

    private int mSelected = 0;      // 索引
    private boolean mRunning;

    private ValueAnimator mBreathAnim;
    private float mLastV = 0f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mRemainSec;
    private final Runnable mCountdown = new Runnable() {
        @Override public void run() {
            if (!mRunning) return;
            mRemainSec--;
            if (mRemainSec <= 0) {
                stopTraining(true);
                return;
            }
            mRemain.setText(formatTime(mRemainSec));
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relax);

        mCircle = findViewById(R.id.rl_circle);
        mHint = findViewById(R.id.rl_hint);
        mRemain = findViewById(R.id.rl_remain);
        mStart = findViewById(R.id.rl_start);

        mChips = new TextView[]{
                findViewById(R.id.rl_d1),
                findViewById(R.id.rl_d2),
                findViewById(R.id.rl_d3)
        };
        for (int i = 0; i < mChips.length; i++) {
            final int idx = i;
            mChips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (mRunning) return;
                    mSelected = idx;
                    refreshChips();
                }
            });
        }
        refreshChips();

        mStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mRunning) stopTraining(false);
                else startTraining();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRunning) stopTraining(false);
    }

    private void refreshChips() {
        for (int i = 0; i < mChips.length; i++) {
            boolean on = i == mSelected;
            mChips[i].setTextColor(on ? 0xFFFE7C48 : 0xFF9A9A9A);
        }
    }

    private void startTraining() {
        mRunning = true;
        mRemainSec = DURATIONS_MIN[mSelected] * 60;
        mRemain.setText(formatTime(mRemainSec));
        mStart.setText("停止");
        // 训练期间保持亮屏（用户主动、且最长 3 分钟）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mLastV = 0.72f;
        mBreathAnim = ValueAnimator.ofFloat(0.72f, 1.0f);
        mBreathAnim.setDuration(4000);
        mBreathAnim.setRepeatCount(ValueAnimator.INFINITE);
        mBreathAnim.setRepeatMode(ValueAnimator.REVERSE);
        mBreathAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mBreathAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float v = (float) a.getAnimatedValue();
                mCircle.setScaleX(v);
                mCircle.setScaleY(v);
                if (v > mLastV + 0.0008f) {
                    if (!"吸气".contentEquals(mHint.getText())) mHint.setText("吸气");
                } else if (v < mLastV - 0.0008f) {
                    if (!"呼气".contentEquals(mHint.getText())) mHint.setText("呼气");
                }
                mLastV = v;
            }
        });
        mBreathAnim.start();

        mHandler.postDelayed(mCountdown, 1000);
    }

    private void stopTraining(boolean finished) {
        mRunning = false;
        mHandler.removeCallbacks(mCountdown);
        if (mBreathAnim != null) {
            mBreathAnim.cancel();
            mBreathAnim = null;
        }
        mCircle.setScaleX(1f);
        mCircle.setScaleY(1f);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mStart.setText("开始");
        if (finished) {
            mHint.setText("完成");
            mRemain.setText("已完成本次呼吸训练");
        } else {
            mHint.setText("准备");
            mRemain.setText("已停止");
        }
    }

    private String formatTime(int sec) {
        return String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60);
    }
}
