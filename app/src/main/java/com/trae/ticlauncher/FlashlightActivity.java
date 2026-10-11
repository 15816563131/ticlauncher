package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * ColorOS 手表：手电筒（复刻 HeyFlashLight）。
 * TicWatch Pro 没有硬件手电筒 → 这里做"屏幕常亮 + 全屏白"作为软手电筒。
 */
public class FlashlightActivity extends Activity {

    private static final String PREF = "flash";
    private static final String KEY_ON = "on";

    private View mBg;
    private ImageView mIcon;
    private TextView mState, mLevel;
    private boolean mOn;

    private CameraManager mCamManager;
    private String mCamId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashlight);

        mBg = findViewById(R.id.fl_bg);
        mIcon = findViewById(R.id.fl_icon);
        mState = findViewById(R.id.fl_state);
        mLevel = findViewById(R.id.fl_level);

        mBg.setBackgroundResource(R.drawable.fl_bg_off);

        // 尝试硬件闪光灯
        mCamManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (mCamManager != null) {
            try {
                for (String id : mCamManager.getCameraIdList()) {
                    CameraCharacteristics c = mCamManager.getCameraCharacteristics(id);
                    Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { mCamId = id; break; }
                }
            } catch (CameraAccessException ignore) {}
        }

        findViewById(R.id.fl_toggle).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggle(); }
        });

        mOn = getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(KEY_ON, false);
        apply(true);
    }

    private void toggle() {
        mOn = !mOn;
        apply(false);
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(KEY_ON, mOn).apply();
    }

    private void apply(boolean init) {
        if (mOn) {
            // 硬件 + 屏幕双保险
            tryFlash(true);

            // 屏幕常亮 + 高亮度（软手电筒）
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 1.0f;
            getWindow().setAttributes(lp);

            mBg.setBackgroundResource(R.drawable.fl_bg_on);
            mIcon.setAlpha(1.0f);
            mState.setText("手电筒开启");
            mState.setTextColor(0xFFFFD84D);
            mLevel.setText("点击圆圈关闭");
            mLevel.setTextColor(0xFF887733);
        } else {
            tryFlash(false);
            mBg.setBackgroundResource(R.drawable.fl_bg_off);
            mIcon.setAlpha(0.5f);
            mState.setText("手电筒关闭");
            mState.setTextColor(0xFF888888);
            mLevel.setText("点击圆圈开启");
            mLevel.setTextColor(0xFF555555);
        }
    }

    private void tryFlash(boolean on) {
        if (mCamManager == null || mCamId == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mCamManager.setTorchMode(mCamId, on);
            }
        } catch (CameraAccessException ignore) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tryFlash(false);
    }
}
