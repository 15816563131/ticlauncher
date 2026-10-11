package com.trae.ticlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * ColorOS 手表：运动记录（复刻 HeySports）。
 *
 * 选一个运动类型 → 进入记录态：计时 + 心率 + 估算消耗。
 *
 * 省电要点：
 *  1) 心率传感器只在「运动中」注册，结束/离开立刻注销；
 *  2) 计时器 1 秒一跳，且只在进行中存在；
 *  3) 不做 GPS 轨迹（TicWatch 上会大幅增加功耗），用时间推算消耗。
 */
public class WorkoutActivity extends Activity implements SensorEventListener {

    private static final int REQ_BODY = 401;

    private static final String[] NAMES = {
            "户外跑步", "室内跑步", "健走", "骑行", "自由训练", "跳绳"
    };
    private static final int[] ICONS = {
            R.drawable.ic_app_sports,
            R.drawable.ic_app_daily,
            R.drawable.ic_app_daily,
            R.drawable.ic_app_sports,
            R.drawable.ic_app_heartrate,
            R.drawable.ic_app_stopwatch
    };
    /** 每种运动每分钟的估算消耗（千卡/分钟）。 */
    private static final float[] KCAL_PER_MIN = {10f, 9f, 5f, 8f, 6f, 12f};

    private View mChooser, mSession;
    private TextView mTypeText, mTime, mHr, mKcal;
    private GridView mGrid;

    private int mTypeIndex = 0;
    private boolean mRunning;
    private int mElapsedSec;
    private int mLastHr = -1;

    private SensorManager mSm;
    private Sensor mHeart;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (!mRunning) return;
            mElapsedSec++;
            renderSession();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout);

        mChooser = findViewById(R.id.wk_chooser);
        mSession = findViewById(R.id.wk_session);
        mTypeText = findViewById(R.id.wk_type);
        mTime = findViewById(R.id.wk_time);
        mHr = findViewById(R.id.wk_hr);
        mKcal = findViewById(R.id.wk_kcal);
        mGrid = findViewById(R.id.wk_grid);

        mGrid.setAdapter(new TypeAdapter());
        mGrid.setOnItemClickListener((parent, view, position, id) -> startSession(position));

        findViewById(R.id.wk_stop).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSession(); }
        });

        mSm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mHeart = mSm != null ? mSm.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;

        if (checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRunning) stopSession();
    }

    private void startSession(int index) {
        mTypeIndex = index;
        mRunning = true;
        mElapsedSec = 0;
        mLastHr = -1;

        mTypeText.setText(NAMES[index]);
        renderSession();

        mChooser.setVisibility(View.GONE);
        mSession.setVisibility(View.VISIBLE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.postDelayed(mTick, 1000);
        if (mHeart != null
                && checkSelfPermission(Manifest.permission.BODY_SENSORS)
                    == PackageManager.PERMISSION_GRANTED) {
            mSm.registerListener(this, mHeart, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopSession() {
        mRunning = false;
        mHandler.removeCallbacks(mTick);
        if (mSm != null) mSm.unregisterListener(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSession.setVisibility(View.GONE);
        mChooser.setVisibility(View.VISIBLE);
    }

    private void renderSession() {
        mTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                mElapsedSec / 60, mElapsedSec % 60));
        int kcal = Math.round(KCAL_PER_MIN[mTypeIndex] * mElapsedSec / 60f);
        mKcal.setText(String.valueOf(kcal));
        mHr.setText(mLastHr > 0 ? String.valueOf(mLastHr) : "--");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mRunning || event.sensor.getType() != Sensor.TYPE_HEART_RATE) return;
        int bpm = Math.round(event.values[0]);
        if (bpm > 0 && bpm < 255) {
            mLastHr = bpm;
            mHr.setText(String.valueOf(bpm));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private class TypeAdapter extends BaseAdapter {
        private final LayoutInflater mInf;

        TypeAdapter() {
            mInf = LayoutInflater.from(WorkoutActivity.this);
        }

        @Override public int getCount() { return NAMES.length; }
        @Override public Object getItem(int p) { return NAMES[p]; }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInf.inflate(R.layout.item_workout, parent, false);
            }
            ((ImageView) convertView.findViewById(R.id.wk_item_icon))
                    .setImageResource(ICONS[position]);
            ((TextView) convertView.findViewById(R.id.wk_item_name))
                    .setText(NAMES[position]);
            return convertView;
        }
    }
}
