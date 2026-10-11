package com.trae.ticlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * 主表盘：复刻官方 ColorOS 表盘「寰宇光尘」。
 *
 * 素材直接取自官方表盘 APK（466x466 设计稿）：
 *   wf_bg        粒子地球底图（橙色配色）
 *   wf_mask      暗角遮罩
 *   wf_ticks_rt  右上刻度弧
 *   wf_ticks_lb  左下刻度弧
 *   wf_d0..wf_d9 图片数字（白色掩码，可着色）
 *   wf_point     刻度游标
 *   wf_arc       右下日期弧形底
 *
 * 角度约定（与官方脚本一致）：0° 在 12 点方向，顺时针为正，单位为 0.1°。
 */
public class WatchFaceView extends View {

    /** 设计稿边长 */
    private static final float DESIGN = 466f;

    private static final int COLOR_ACCENT = 0xFFFF5416;
    private static final int COLOR_LABEL = 0xFFBFBFBF;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private Bitmap mBg, mMask, mTicksRt, mTicksLb, mArc, mPoint;
    private final Bitmap[] mDigits = new Bitmap[10];
    private Typeface mTypeface;

    /**
     * 位图缓存（进程级）。
     * 这些素材共 17 张、其中多张是 466×466，同步解码要 1s 以上；
     * 如果每次 Activity 重建都解一遍，回到桌面时会先黑屏/灰屏几百毫秒。
     * 因此只在进程内第一次用到时解码，之后所有实例共用。
     */
    private static Bitmap sBg, sMask, sTicksRt, sTicksLb, sArc, sPoint;
    private static final Bitmap[] sDigits = new Bitmap[10];
    private static boolean sLoaded;

    private final Calendar mCalendar = Calendar.getInstance();

    private SensorManager mSensorManager;
    private int mSteps = -1;

    private final BroadcastReceiver mTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidate();
        }
    };

    /** 注册步数传感器，每收到一次 TYPE_STEP_COUNTER 就更新表盘显示。 */
    private final SensorEventListener mStepListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                StepSource d = StepSource.compute(getContext(), event.values[0]);
                if (d.valid()) {
                    mSteps = d.steps;
                    invalidate();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public WatchFaceView(Context context) {
        super(context);
        init(context);
    }

    public WatchFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        Resources r = context.getResources();
        if (!sLoaded) {
            sBg = decode(r, R.drawable.wf_bg);
            sMask = decode(r, R.drawable.wf_mask);
            sTicksRt = decode(r, R.drawable.wf_ticks_rt);
            sTicksLb = decode(r, R.drawable.wf_ticks_lb);
            sArc = decode(r, R.drawable.wf_arc);
            sPoint = decode(r, R.drawable.wf_point);
            for (int i = 0; i < 10; i++) {
                int id = r.getIdentifier("wf_d" + i, "drawable", context.getPackageName());
                sDigits[i] = id == 0 ? null : decode(r, id);
            }
            sLoaded = true;
        }
        mBg = sBg;
        mMask = sMask;
        mTicksRt = sTicksRt;
        mTicksLb = sTicksLb;
        mArc = sArc;
        mPoint = sPoint;
        System.arraycopy(sDigits, 0, mDigits, 0, 10);
        mTypeface = r.getFont(R.font.oplusans_watch);
        mTextPaint.setTypeface(mTypeface);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private static Bitmap decode(Resources r, int id) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        return BitmapFactory.decodeResource(r, id, o);
    }

    // ---------- 传感器 ----------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().registerReceiver(mTickReceiver,
                new IntentFilter(Intent.ACTION_TIME_TICK));
        mSensorManager = (SensorManager) getContext()
                .getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            Sensor step = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (step != null) {
                mSensorManager.registerListener(mStepListener, step, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            getContext().unregisterReceiver(mTickReceiver);
        } catch (Exception ignored) {
        }
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mStepListener);
        }
    }

    // ---------- 绘制 ----------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float s = w / DESIGN;
        float cx = w / 2f;
        float cy = h / 2f;

        canvas.drawColor(Color.BLACK);

        // 底图 + 暗角
        drawBitmap(canvas, mBg, 0, 0, 468, 468, s);
        drawBitmap(canvas, mMask, 0, 0, 468, 468, s);

        // 刻度弧（右上 / 左下）
        drawBitmap(canvas, mTicksRt, 148, 0, 272, 104, s);
        drawBitmap(canvas, mTicksLb, 0, 254, 212, 212, s);

        // 右下日期弧形底 + 日期（顺时针居中 118.6°）
        drawTinted(canvas, mArc, 368, 264, 112, 148, s, COLOR_ACCENT);
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        String date = String.format(Locale.getDefault(), "%02d/%02d %s",
                mCalendar.get(Calendar.MONTH) + 1,
                mCalendar.get(Calendar.DAY_OF_MONTH),
                weekday(mCalendar.get(Calendar.DAY_OF_WEEK)));
        drawArcText(canvas, cx, cy, date, 203f * s, 118.6f, true,
                Align.CENTER, 21f * s, 0xFF1A1A1A);

        // 弧形标签：文字沿圆弧顺时针排列，字形朝外，基线半径 192（落在刻度带内侧且不贴边）
        drawArcText(canvas, cx, cy, "今日步数", 192f * s, -20f, true,
                Align.CENTER, 21f * s, COLOR_LABEL);
        drawArcText(canvas, cx, cy, stepsText(), 192f * s, 75f, true,
                Align.CENTER, 21f * s, COLOR_LABEL);
        // 海拔文案已移除：本设备无气压计（TYPE_PRESSURE 不存在）

        // 刻度游标：随进度沿右上 / 左下刻度弧移动
        float frac = mSteps < 0 ? 0f : Math.min(1f, mSteps / 8000f);
        drawRotated(canvas, mPoint, 229, 7, 16, 20, s, COLOR_ACCENT,
                cx, cy, -20.8f + frac * 71.6f);
        drawRotated(canvas, mPoint, 229, 7, 16, 20, s, COLOR_ACCENT,
                cx, cy, -99.2f - frac * 71.6f);

        // 时间：上排小时(白) / 下排分钟(橙)
        int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        int min = mCalendar.get(Calendar.MINUTE);
        drawDigit(canvas, hour / 10, 153, 148, s, COLOR_WHITE);
        drawDigit(canvas, hour % 10, 233, 148, s, COLOR_WHITE);
        drawDigit(canvas, min / 10, 153, 236, s, COLOR_ACCENT);
        drawDigit(canvas, min % 10, 233, 236, s, COLOR_ACCENT);
    }

    private String stepsText() {
        return mSteps < 0 ? "--步" : String.format(Locale.getDefault(), "%d步", mSteps);
    }

    private void drawBitmap(Canvas canvas, Bitmap b, float x, float y,
                            float bw, float bh, float s) {
        if (b == null) {
            return;
        }
        mRect.set(x * s, y * s, (x + bw) * s, (y + bh) * s);
        canvas.drawBitmap(b, null, mRect, mPaint);
    }

    private void drawTinted(Canvas canvas, Bitmap b, float x, float y,
                            float bw, float bh, float s, int color) {
        if (b == null) {
            return;
        }
        mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        mRect.set(x * s, y * s, (x + bw) * s, (y + bh) * s);
        canvas.drawBitmap(b, null, mRect, mPaint);
        mPaint.setColorFilter(null);
    }

    private void drawDigit(Canvas canvas, int digit, float x, float y,
                           float s, int color) {
        if (digit < 0 || digit > 9) {
            return;
        }
        Bitmap b = mDigits[digit];
        if (b == null) {
            return;
        }
        drawTinted(canvas, b, x, y, 80, 84, s, color);
    }

    /** 绕表盘中心旋转后再绘制（用于刻度游标随进度移动）。 */
    private void drawRotated(Canvas canvas, Bitmap b, float x, float y,
                             float bw, float bh, float s, int color,
                             float cx, float cy, float angleDeg) {
        if (b == null) {
            return;
        }
        canvas.save();
        canvas.rotate(angleDeg, cx, cy);
        drawTinted(canvas, b, x, y, bw, bh, s, color);
        canvas.restore();
    }

    private enum Align { LEFT, CENTER, RIGHT }

    /**
     * 沿圆弧绘制文字。
     *
     * @param radius    基线圆半径（像素）
     * @param angleDeg  对齐参考角，0° 在 12 点方向，顺时针为正
     * @param clockwise true 顺时针排字，false 逆时针
     */
    private void drawArcText(Canvas canvas, float cx, float cy, String text,
                             float radius, float angleDeg, boolean clockwise,
                             Align align, float textSize, int color) {
        if (text == null || text.isEmpty() || radius <= 0) {
            return;
        }
        mTextPaint.setTextSize(textSize);
        mTextPaint.setColor(color);

        // 计算文字总角宽
        float[] widths = new float[text.length()];
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            widths[i] = mTextPaint.measureText(c);
            total += widths[i];
        }
        float totalRad = total / radius;
        float totalDeg = (float) Math.toDegrees(totalRad);

        float start;
        if (clockwise) {
            start = align == Align.RIGHT ? angleDeg - totalDeg
                    : align == Align.CENTER ? angleDeg - totalDeg / 2f : angleDeg;
        } else {
            start = align == Align.LEFT ? angleDeg
                    : align == Align.CENTER ? angleDeg + totalDeg / 2f : angleDeg + totalDeg;
        }

        float acc = 0f;
        for (int i = 0; i < text.length(); i++) {
            float halfRad = (widths[i] / 2f) / radius;
            float charDeg = (float) Math.toDegrees(halfRad);
            float at = clockwise ? start + acc + charDeg : start - acc - charDeg;
            canvas.save();
            canvas.rotate(at, cx, cy);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(text.charAt(i)), cx, cy - radius, mTextPaint);
            canvas.restore();
            acc += (float) Math.toDegrees(widths[i] / radius);
        }
    }

    private static String weekday(int day) {
        switch (day) {
            case Calendar.MONDAY: return "周一";
            case Calendar.TUESDAY: return "周二";
            case Calendar.WEDNESDAY: return "周三";
            case Calendar.THURSDAY: return "周四";
            case Calendar.FRIDAY: return "周五";
            case Calendar.SATURDAY: return "周六";
            default: return "周日";
        }
    }
}
