package com.trae.ticlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 把任意应用图标统一裁成圆形，复刻 ColorOS Watch 应用列表里一致的圆形图标观感。
 *
 * 实现上先离屏渲染成一张圆形位图并缓存，之后每帧只做一次 drawBitmap，
 * 避免在列表滚动 / 星图逐层动画时反复裁剪路径导致掉帧。
 */
public class CircleIconDrawable extends Drawable {

    private final Drawable mSrc;
    private final Paint mMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap mCache;
    private int mCacheSize;

    public CircleIconDrawable(Drawable src) {
        mSrc = src;
        mMaskPaint.setColor(Color.BLACK);
        mMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    private void ensureCache(int size) {
        if (mCache != null && mCacheSize == size) {
            return;
        }
        if (size <= 0) {
            return;
        }
        if (mCache != null) {
            mCache.recycle();
            mCache = null;
        }
        mCacheSize = size;
        mCache = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mCache);

        int w = mSrc.getIntrinsicWidth();
        int h = mSrc.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = size;
            h = size;
        }
        float scale = Math.min(size / (float) w, size / (float) h);
        int dw = Math.max(1, Math.round(w * scale));
        int dh = Math.max(1, Math.round(h * scale));
        int left = (size - dw) / 2;
        int top = (size - dh) / 2;
        mSrc.setBounds(left, top, left + dw, top + dh);
        mSrc.draw(c);

        // 圆形遮罩：只保留圆内像素
        c.drawCircle(size / 2f, size / 2f, size / 2f, mMaskPaint);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        int size = Math.min(b.width(), b.height());
        ensureCache(size);
        if (mCache == null) {
            return;
        }
        float half = size / 2f;
        canvas.drawBitmap(mCache, b.exactCenterX() - half, b.exactCenterY() - half, null);
    }

    @Override
    public void setAlpha(int alpha) {
        // 位图已合成，透明度由 View 的 alpha 统一控制
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        outline.setOval(b.left, b.top, b.right, b.bottom);
    }

    /** 统一裁剪；空图标原样返回。 */
    public static Drawable of(Drawable src) {
        return src == null ? null : new CircleIconDrawable(src);
    }
}
