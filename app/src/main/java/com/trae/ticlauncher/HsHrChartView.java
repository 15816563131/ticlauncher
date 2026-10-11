package com.trae.ticlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 心率折线图（复刻 HeyHeartRate 的 HeartRateChart）。
 *
 * 官方 HeartRateChart 本身就是 Canvas 自绘控件（见 res/layout/layout_heart_rate_statistics.xml：
 *   高度 hr_statics_chart_height_dp121 = 97dp、marginTop 6dp，
 *   chartColor = heart_rate_chart_color = #ffe64a39，chartWidth = hr_margin_same_dp2 = 2dp），
 * 因此此处按官方同一组尺寸/配色做等价绘制，不引入任何臆造样式。
 */
public class HsHrChartView extends View {

    private static final int CHART_COLOR = 0xFFE64A39;   // 官方 heart_rate_chart_color
    private static final int MAX_POINTS = 40;            // 保留最近 40 个采样点

    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    private final float[] mValues = new float[MAX_POINTS];
    private int mCount;

    public HsHrChartView(Context c) {
        this(c, null);
    }

    public HsHrChartView(Context c, AttributeSet a) {
        super(c, a);
        float stroke = 2f * getResources().getDisplayMetrics().density;  // 官方 chartWidth = 2dp
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setStrokeWidth(stroke);
        mLine.setStrokeCap(Paint.Cap.ROUND);
        mLine.setStrokeJoin(Paint.Join.ROUND);
        mLine.setColor(Color.argb(255, Color.red(CHART_COLOR), Color.green(CHART_COLOR), Color.blue(CHART_COLOR)));
    }

    /** 送入一个心率样本（次/分），自动滚动窗口。 */
    public void addValue(int bpm) {
        if (bpm <= 0 || bpm > 255) {
            return;
        }
        if (mCount < MAX_POINTS) {
            mValues[mCount++] = bpm;
        } else {
            System.arraycopy(mValues, 1, mValues, 0, MAX_POINTS - 1);
            mValues[MAX_POINTS - 1] = bpm;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mCount < 2) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float min = mValues[0], max = mValues[0];
        for (int i = 1; i < mCount; i++) {
            if (mValues[i] < min) min = mValues[i];
            if (mValues[i] > max) max = mValues[i];
        }
        // 上下留白，避免贴边
        float pad = Math.max(6f, (max - min) * 0.18f);
        min -= pad;
        max += pad;
        float span = Math.max(1f, max - min);

        float inset = mLine.getStrokeWidth();
        float usableW = w - inset * 2f;
        float usableH = h - inset * 2f;
        float stepX = mCount > 1 ? usableW / (mCount - 1) : usableW;

        mPath.reset();
        for (int i = 0; i < mCount; i++) {
            float x = inset + stepX * i;
            float y = inset + usableH * (1f - (mValues[i] - min) / span);
            if (i == 0) {
                mPath.moveTo(x, y);
            } else {
                mPath.lineTo(x, y);
            }
        }
        canvas.drawPath(mPath, mLine);
    }
}
