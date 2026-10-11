package com.trae.ticlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * 星图（Radial）应用列表。
 *
 * 复刻 OPPO Watch X3 的星图桌面：
 *  · 图标按「半格偏移的正方形网格」铺满圆屏 —— 中间四个等距、同样大，四向对称规整；
 *  · 拖动屏幕是「平移星图」（不是整体旋转）；
 *  · 采用鱼眼式径向映射：越靠屏幕中心位置被拉得越开、图标越大，
 *    越往外圈越收紧、图标明显缩小，形成纵深；
 *  · 点按图标即启动。
 */
public class RadialAppView extends ViewGroup {

    private static final String PREFS = "radial";

    /** 六边形网格间距（dp）—— 越大图标越疏。参考图约 4~5 个/直径。 */
    private static final float GRID_DP = 52f;
    /** 图标边长 / 网格间距（基准），保证相邻图标贴近但不重叠。 */
    private static final float ICON_RATIO = 0.9f;
    /** 网格范围（行/列跨度），够铺满圆屏即可。 */
    private static final int GRID_SPAN = 6;
    /** 星图最少能被拖离中心的距离（dp）—— 实际范围会按最外圈图标自动放大。 */
    private static final float PAN_LIMIT_DP = 40f;

    /**
     * 鱼眼强度：中心区域的位置被拉开、外圈收紧，
     * 于是中心能容纳「放大的图标」而不与邻居重叠，边缘图标则明显缩小。
     */
    private static final float MAG = 0.36f;

    private float mDp;
    private int mTouchSlop;

    private int[] mRing = new int[0];
    private int[] mSlot = new int[0];
    /** 每个图标的原始坐标（相对屏幕中心，未平移） */
    private float[] mBaseX = new float[0];
    private float[] mBaseY = new float[0];
    /** 图标统一的基准边长（px） */
    private int mIconSize;

    /** 星图平移量 */
    private float mPanX = 0f;
    private float mPanY = 0f;
    private float mCx, mCy, mScaleRadius, mInnerRadius, mPanLimit;

    private boolean mDragging;
    private float mDownX, mDownY, mLastX, mLastY;

    public RadialAppView(Context context) {
        super(context);
        init(context);
    }

    public RadialAppView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RadialAppView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mDp = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusableInTouchMode(true);
        mPanLimit = PAN_LIMIT_DP * mDp;
        // 按「中心最大」的尺寸测量子 View，之后一律只缩小不放大，保证清晰
        mIconSize = Math.round(GRID_DP * ICON_RATIO * (1f + MAG) * mDp);
    }

    // ---------- 绑定数据 ----------

    public void bind(List<AppEntry> apps,
                     OnClickListener click,
                     OnLongClickListener longClick) {
        removeAllViews();
        int n = apps == null ? 0 : apps.size();
        if (n == 0) {
            mRing = new int[0];
            mSlot = new int[0];
            mBaseX = new float[0];
            mBaseY = new float[0];
            requestLayout();
            return;
        }

        // 生成「半格偏移」的正方形网格点，再按离中心的远近排序后取最近的 n 个。
        // 半格偏移保证没有点落在正中心，最靠近的是等距的四个点 ——
        // 于是屏幕中间会出现四个同样大的图标，四向对称、布局规整。
        float spacing = GRID_DP * mDp;
        List<float[]> cells = new ArrayList<>();
        for (int r = -GRID_SPAN; r <= GRID_SPAN; r++) {
            float y = (r + 0.5f) * spacing;
            for (int c = -GRID_SPAN; c <= GRID_SPAN; c++) {
                float x = (c + 0.5f) * spacing;
                cells.add(new float[]{x, y, x * x + y * y});
            }
        }
        java.util.Collections.sort(cells, new java.util.Comparator<float[]>() {
            @Override
            public int compare(float[] a, float[] b) {
                return Float.compare(a[2], b[2]);
            }
        });

        int count = Math.min(n, cells.size());
        float maxDist = 1f;
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float d = (float) Math.sqrt(cells.get(i)[2]);
            maxDist = Math.max(maxDist, d);
            minDist = Math.min(minDist, d);
        }
        // 缩放从「最靠内的那圈」开始算起，让中间四个图标拿到最大尺寸
        mInnerRadius = minDist == Float.MAX_VALUE ? 0f : minDist;

        mRing = new int[count];
        mSlot = new int[count];
        mBaseX = new float[count];
        mBaseY = new float[count];

        for (int i = 0; i < count; i++) {
            AppEntry e = apps.get(i);
            float[] cell = cells.get(i);
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(CircleIconDrawable.of(e.icon));
            iv.setTag(e);
            if (click != null) {
                iv.setOnClickListener(click);
            }
            if (longClick != null) {
                iv.setOnLongClickListener(longClick);
            }
            addView(iv);
            // 按距离分成若干档，仅用于进入动效的错峰
            mRing[i] = Math.min(3, (int) (Math.sqrt(cell[2]) / maxDist * 4f));
            mSlot[i] = i;
            mBaseX[i] = cell[0];
            mBaseY[i] = cell[1];
        }

        hideAll();
        updatePanLimit();
        requestLayout();
    }

    /**
     * 按「最外圈图标」自动放大可拖动范围：保证任何一张图标都能被拖到屏幕正中，
     * 否则圆屏边缘的图标很难点中。
     */
    private void updatePanLimit() {
        float maxD = 0f;
        for (int i = 0; i < mBaseX.length; i++) {
            float d = (float) Math.hypot(mBaseX[i], mBaseY[i]);
            if (d > maxD) {
                maxD = d;
            }
        }
        // 留半格余量，让最外圈图标也能稳稳停到中心
        float dynamic = maxD + GRID_DP * mDp * 0.5f;
        mPanLimit = Math.max(PAN_LIMIT_DP * mDp, dynamic);
    }

    // ---------- 测量 / 布局 ----------

    private int iconSizePx(int index) {
        return mIconSize;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            int s = iconSizePx(i);
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mCx = (r - l) / 2f;
        mCy = (b - t) / 2f;
        mScaleRadius = Math.min(mCx, mCy);
        // 所有子 View 先统一摆在正中，实际位置靠 translation 摆到星图各处
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            int cw = c.getMeasuredWidth();
            int ch = c.getMeasuredHeight();
            c.layout(Math.round(mCx - cw / 2f), Math.round(mCy - ch / 2f),
                    Math.round(mCx + cw / 2f), Math.round(mCy + ch / 2f));
        }
        applyTransforms();
    }

    private float clampPan(float v) {
        if (v > mPanLimit) {
            return mPanLimit;
        }
        if (v < -mPanLimit) {
            return -mPanLimit;
        }
        return v;
    }

    /**
     * 摆放图标：
     *  · 位置 = 原始坐标 + 平移，再按「鱼眼」做径向重映射 —— 越靠中心被拉得越开；
     *  · 尺寸与「局部间距」同步：中心大到接近满格、外圈明显缩小，形成星图纵深。
     */
    private void applyTransforms() {
        if (mScaleRadius <= 0f) {
            return;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (i >= mBaseX.length) {
                continue;
            }
            float x = mBaseX[i] + mPanX;
            float y = mBaseY[i] + mPanY;
            float d = (float) Math.hypot(x, y);
            float span = mScaleRadius - mInnerRadius;
            if (span < 1f) {
                span = 1f;
            }
            float t = (d - mInnerRadius) / span;
            if (t < 0f) {
                t = 0f;
            } else if (t > 1f) {
                t = 1f;
            }

            // 位置放大倍率：中心 1+MAG，外圈 1
            float mag = 1f + MAG * (1f - t);
            c.setTranslationX(x * mag);
            c.setTranslationY(y * mag);

            // 尺寸随局部间距变化：中心 1+MAG，外圈 1-MAG，再归一化到测量尺寸
            float local = 1f + MAG * (1f - 2f * t);
            float scale = local / (1f + MAG);
            c.setScaleX(scale);
            c.setScaleY(scale);
            c.setAlpha(1f - 0.18f * t);
        }
    }

    // ---------- 手势：拖动平移星图 ----------

    /**
     * 手指按下时若落在图标上，由子 View 先拿到 DOWN，这里在移动超过阈值后「抢过来」；
     * 若落在图标之间的空隙（没有子 View 消费 DOWN），则交由下面的 onTouchEvent 处理。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTouch(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging
                        && Math.hypot(ev.getX() - mDownX, ev.getY() - mDownY) > mTouchSlop) {
                    mDragging = true;
                    mLastX = ev.getX();
                    mLastY = ev.getY();
                    cancelChildTouch();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTouch(ev);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = ev.getX();
                float y = ev.getY();
                if (!mDragging) {
                    if (Math.hypot(x - mDownX, y - mDownY) <= mTouchSlop) {
                        break;
                    }
                    mDragging = true;
                }
                mPanX = clampPan(mPanX + x - mLastX);
                mPanY = clampPan(mPanY + y - mLastY);
                mLastX = x;
                mLastY = y;
                applyTransforms();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    mDragging = false;
                    savePan();
                }
                return true;
            default:
                break;
        }
        return mDragging;
    }

    private void startTouch(MotionEvent ev) {
        mDownX = ev.getX();
        mDownY = ev.getY();
        mLastX = mDownX;
        mLastY = mDownY;
        mDragging = false;
    }

    /** 表冠：纵向逛星图。 */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float v = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            if (v != 0f) {
                mPanY = clampPan(mPanY - v * mDp * 22f);
                applyTransforms();
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    /** 拖动开始前给子 View 补发 CANCEL，避免其残留按下态误触发点击。 */
    private void cancelChildTouch() {
        MotionEvent cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    // ---------- 星图位置记忆 ----------
    // 这台手表的系统会把「左→右」拖动吞掉并结束桌面，桌面会被自动拉回来；
    // 把位置存下来，拉回来之后还能停在原处，不会「白逛」。

    private void savePan() {
        try {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putFloat("panX", mPanX).putFloat("panY", mPanY).apply();
        } catch (Exception ignored) {
        }
    }

    private void restorePan() {
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            mPanX = clampPan(sp.getFloat("panX", 0f));
            mPanY = clampPan(sp.getFloat("panY", 0f));
        } catch (Exception ignored) {
        }
    }

    // ---------- 进入动效 ----------

    public void hideAll() {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.animate().cancel();
            c.setAlpha(0f);
        }
    }

    /** 由内向外逐环浮现。 */
    public void playEnter() {
        float span = Math.max(1f, mScaleRadius - mInnerRadius);
        for (int i = 0; i < getChildCount() && i < mBaseX.length; i++) {
            View c = getChildAt(i);
            float t = ((float) Math.hypot(mBaseX[i] + mPanX, mBaseY[i] + mPanY) - mInnerRadius) / span;
            t = Math.max(0f, Math.min(1f, t));
            long delay = 40L + mRing[i] * 90L + mSlot[i] * 18L;
            c.animate()
                    .alpha(1f - 0.18f * t)
                    .setStartDelay(delay)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restorePan();
        requestFocus();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            applyTransforms();
        }
    }
}

