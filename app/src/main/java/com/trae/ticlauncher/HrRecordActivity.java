package com.trae.ticlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

/**
 * ColorOS 手表：心率记录/统计页（复刻 HeyHeartRate 的第二页）。
 *
 * 页面结构完全对照官方 layout_heart_content.xml + layout_heart_rate_statistics.xml 还原：
 *   标题「心率」→ 当前心率（ic_record + 数值 + 次/分）→ 测量状态 → 心率范围 → 静息心率 →
 *   步行平均心率（官方默认隐藏）→ 睡眠基准心率 → 「了解心率」按钮。
 * 素材取自解包后的 HeyHeartRate APK，无任何自绘；官方顶部折线图（HeartRateChart，Canvas 自绘）
 * 按「禁止绘制」原则未绘制。
 *
 * 数据源：标准 {@link Sensor#TYPE_HEART_RATE}（TicWatch Pro = PixArt PAH8011），
 * 不引用任何 com.oppo / com.heytap 类或 OPPO 私有框架。
 * 会话内实时统计：心率范围 = 本次最小~最大，静息心率 = 本次最小。
 */
public class HrRecordActivity extends Activity implements SensorEventListener {

    private static final int REQ_BODY = 102;

    private TextView mCurValue, mCurStatus;
    private TextView mRangeValue, mRestValue, mSleepValue;
    private HsHrChartView mChart;

    private SensorManager mSensorManager;
    private Sensor mHeartSensor;
    private boolean mGranted;

    private int mCurrent = -1;
    private int mMin = -1;
    private int mMax = -1;
    private long mSum;
    private int mCount;

    private ValueAnimator mBpmAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr_record);

        mCurValue = findViewById(R.id.hr_cur_value);
        mCurStatus = findViewById(R.id.hr_cur_status);
        mRangeValue = findViewById(R.id.hr_range_value);
        mRestValue = findViewById(R.id.hr_rest_value);
        mSleepValue = findViewById(R.id.hr_sleep_value);
        mChart = findViewById(R.id.hr_chart);

        findViewById(R.id.hr_rec_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeartSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;
        if (mHeartSensor == null) {
            mCurStatus.setText("设备不支持心率");
            mCurStatus.setTextColor(0xFFFF5416);
        }

        mGranted = checkSelfPermission(Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_GRANTED;
        if (!mGranted) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY);
        }

        setupSwipeBack();
    }

    /** 官方为 ViewPager2 两页左右滑动，这里用「右滑返回测量页」还原该交互。 */
    private void setupSwipeBack() {
        final GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) { return true; }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float vx, float vy) {
                        if (e1 != null && e2 != null
                                && e2.getX() - e1.getX() > 60 && Math.abs(vx) > Math.abs(vy)) {
                            finish();
                            return true;
                        }
                        return false;
                    }
                });
        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                return detector.onTouchEvent(e);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BODY) {
            mGranted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (mGranted) startMeasure();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGranted && mHeartSensor != null) startMeasure();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMeasure();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBpmAnim != null) { mBpmAnim.cancel(); mBpmAnim = null; }
    }

    private void startMeasure() {
        if (mHeartSensor == null) return;
        mCurStatus.setText("正在测量");
        mCurStatus.setTextColor(0xFFB0B5BF);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager.registerListener(this, mHeartSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopMeasure() {
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---------- 传感器 ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HEART_RATE) return;
        int bpm = Math.round(event.values[0]);
        if (bpm <= 0 || bpm > 255) return;

        if (mMin < 0 || bpm < mMin) mMin = bpm;
        if (bpm > mMax) mMax = bpm;
        mSum += bpm;
        mCount++;

        animateCurrent(bpm);
        mChart.addValue(bpm);
        mRestValue.setText(String.valueOf(mMin));
        mRangeValue.setText(mMin + "-" + mMax);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ---------- 数值平滑过渡（避免瞬间跳变） ----------

    private void animateCurrent(int newBpm) {
        int from = mCurrent < 0 ? (newBpm - 1) : mCurrent;
        mCurrent = newBpm;
        if (mBpmAnim != null) { mBpmAnim.cancel(); mBpmAnim = null; }
        mBpmAnim = ValueAnimator.ofInt(from, newBpm);
        mBpmAnim.setDuration(500);
        mBpmAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        mBpmAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                mCurValue.setText(String.valueOf((int) a.getAnimatedValue()));
            }
        });
        mBpmAnim.start();
    }
}
