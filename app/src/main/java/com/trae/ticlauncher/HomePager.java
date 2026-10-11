package com.trae.ticlauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/**
 * 表盘首页的横向分页容器。
 *
 * 共三页：负一屏（健康）/ 表盘主页 / 卡片页，默认停在中间的表盘页。
 * 左右滑动切换，松手后自动吸附到最近的一页。
 *
 * 翻页逻辑：
 *   - 拖动距离超过 1/4 屏宽 → 翻到上一页/下一页
 *   - 或者释放瞬间横向速度 > 1000 px/s → 也翻页（快速甩一下也能翻）
 *   - settle 动画时长根据距离自适应（140ms ~ 320ms），避免长距离太慢短距离太快
 *   - 首尾到边界即弹回，不做循环回卷（避免卡片页继续左滑跳到负一屏）
 */
public class HomePager extends ViewGroup {

    /** 默认页：表盘主页 */
    public static final int PAGE_WATCH_FACE = 1;

    private final Scroller mScroller;
    private final int mTouchSlop;
    private final float mDp;
    private final int mMinFlingVelocity;

    private boolean mDragging;
    private float mDownX, mDownY, mLastX;
    /** 本次横向拖动的累计位移（手指向左为正）。 */
    private float mDragDistance;

    private VelocityTracker mVelocityTracker;

    private int mPage = PAGE_WATCH_FACE;
    private OnPageChangedListener mListener;
    private Runnable mOnGesture;

    private final android.graphics.Paint mDotPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    public interface OnPageChangedListener {
        void onPageChanged(int page);
    }

    public HomePager(Context context) {
        this(context, null);
    }

    public HomePager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HomePager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScroller = new Scroller(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMinFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mDp = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    public int getPage() {
        return mPage;
    }

    public void setOnPageChangedListener(OnPageChangedListener l) {
        mListener = l;
    }

    /** 横向手势开始时的回调：用于给「系统把横向滑动误判成返回」加保护。 */
    public void setOnGestureListener(Runnable r) {
        mOnGesture = r;
    }

    public void setPage(int page, boolean animate) {
        int count = getChildCount();
        if (count == 0) {
            return;
        }
        page = Math.max(0, Math.min(count - 1, page));
        int w = pageWidth();
        if (w <= 0) {
            mPage = page;
            return;
        }
        int to = page * w;
        int from = getScrollX();
        boolean changed = page != mPage;
        mPage = page;
        if (!animate) {
            scrollTo(to, 0);
            invalidate();
        } else {
            int distance = Math.abs(to - from);
            // 自适应时长：140ms 起步，每像素加 0.6ms，封顶 320ms
            int duration = (int) Math.max(140, Math.min(320, distance * 0.6f));
            mScroller.startScroll(from, 0, to - from, 0, duration);
            postInvalidateOnAnimation();
        }
        if (changed && mListener != null) {
            mListener.onPageChanged(page);
        }
    }

    private int pageWidth() {
        return getWidth();
    }

    // ---------- 测量 / 布局 ----------

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.layout(i * w, 0, (i + 1) * w, b - t);
        }
        if (!mScroller.computeScrollOffset()) {
            scrollTo(mPage * w, 0);
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    // ---------- 手势 ----------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mLastX = mDownX;
                mDragDistance = 0f;
                mDragging = false;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging) {
                    float dx = ev.getX() - mDownX;
                    float dy = ev.getY() - mDownY;
                    if (Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                        mDragging = true;
                        mLastX = ev.getX();
                        ensureVelocityTracker();
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        ensureVelocityTracker();
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    float x = ev.getX();
                    int delta = Math.round(mLastX - x);
                    mLastX = x;
                    mDragDistance += delta;
                    int max = (getChildCount() - 1) * pageWidth();
                    int nx = getScrollX() + delta;
                    if (nx < 0) {
                        nx = 0;
                    } else if (nx > max) {
                        nx = max;
                    }
                    scrollTo(nx, 0);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    mDragging = false;
                    int w = pageWidth();
                    int count = getChildCount();
                    int page = mPage;
                    if (w > 0 && count > 1) {
                        // 计算释放瞬间的横向速度（px/s，手指向左滑为正）
                        float velX = 0f;
                        if (mVelocityTracker != null) {
                            mVelocityTracker.computeCurrentVelocity(1000);
                            velX = mVelocityTracker.getXVelocity();
                        }

                        boolean shouldFlip = false;
                        if (mDragDistance < -w / 4f) {
                            page = mPage - 1;        // 手指向右 → 上一页（左边）
                            shouldFlip = true;
                        } else if (mDragDistance > w / 4f) {
                            page = mPage + 1;        // 手指向左 → 下一页（右边）
                            shouldFlip = true;
                        } else if (Math.abs(velX) > 1000) {
                            // 快速甩一下，即使没滑过 1/4 屏也翻页。
                            // velX < 0 表示手指向左 → 下一页；velX > 0 向右 → 上一页。
                            if (velX < 0) {
                                page = mPage + 1;
                            } else {
                                page = mPage - 1;
                            }
                            shouldFlip = true;
                        }

                        // 首尾不再循环回卷：滑到边界就弹回，避免在卡片页继续左滑时
                        // 一下跳到最左侧的负一屏。
                        if (shouldFlip && page >= 0 && page < count) {
                            setPage(page, true);
                        } else {
                            // 没达到翻页阈值 / 已到边界 → 弹回原位置，走动画
                            setPage(mPage, true);
                        }
                    }
                    if (mDragDistance > mTouchSlop && mOnGesture != null) {
                        mOnGesture.run();
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return mDragging;
    }

    private void ensureVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    // ---------- 页码指示点 ----------

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        int count = getChildCount();
        if (count < 2) {
            return;
        }
        float r = mDp * 1.7f;
        float gap = mDp * 5.5f;
        float total = count * r * 2 + (count - 1) * (gap - r * 2);
        float x = (getWidth() - total) / 2f + r;
        float y = getHeight() - mDp * 13f;
        for (int i = 0; i < count; i++) {
            boolean on = i == mPage;
            mDotPaint.setColor(on ? 0xFFFF5416 : 0x59FFFFFF);
            canvas.drawCircle(x + i * gap, y, on ? r * 1.15f : r, mDotPaint);
        }
    }
}
