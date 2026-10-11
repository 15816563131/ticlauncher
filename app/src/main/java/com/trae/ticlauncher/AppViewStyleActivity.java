package com.trae.ticlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

/**
 * ColorOS 手表：「应用视图」弹窗（复刻官方 dialog_applist_edit.xml）。
 *
 * 官方提供三种应用列表视图（文案取自官方 values-zh-rCN/strings.xml）：
 *   行星视图（honeycomb）→ {@link AppListAdapter#MODE_STAR}
 *   宫格视图（lattice）  → {@link AppListAdapter#MODE_GRID}
 *   列表视图（vertical） → {@link AppListAdapter#MODE_LIST}
 * 进出场动画与官方 applist_edit_dialog_in/out 一致：scale 0.9↔1.0 + alpha，200ms/250ms。
 */
public class AppViewStyleActivity extends Activity {

    private ImageView mRadioHoneycomb, mRadioLattice, mRadioVertical;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_view);
        overridePendingTransition(R.anim.app_view_in, 0);

        mRadioHoneycomb = findViewById(R.id.radio_honeycomb);
        mRadioLattice = findViewById(R.id.radio_lattice);
        mRadioVertical = findViewById(R.id.radio_vertical);

        bind(R.id.opt_honeycomb, AppListAdapter.MODE_STAR);
        bind(R.id.opt_lattice, AppListAdapter.MODE_GRID);
        bind(R.id.opt_vertical, AppListAdapter.MODE_LIST);

        refreshRadios();
    }

    private void bind(int viewId, final int mode) {
        View row = findViewById(viewId);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pressFeedback(v);
                setStyle(mode);
            }
        });
    }

    /** 按压回弹，与列表页一致的轻量反馈。 */
    private void pressFeedback(final View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                }).start();
    }

    private void setStyle(int mode) {
        getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .edit().putInt(SettingsActivity.KEY_LIST_STYLE, mode).apply();
        refreshRadios();
    }

    private void refreshRadios() {
        int cur = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getInt(SettingsActivity.KEY_LIST_STYLE, AppListAdapter.MODE_STAR);
        mRadioHoneycomb.setImageResource(cur == AppListAdapter.MODE_STAR
                ? R.drawable.hs_radio_on : R.drawable.hs_radio_off);
        mRadioLattice.setImageResource(cur == AppListAdapter.MODE_GRID
                ? R.drawable.hs_radio_on : R.drawable.hs_radio_off);
        mRadioVertical.setImageResource(cur == AppListAdapter.MODE_LIST
                ? R.drawable.hs_radio_on : R.drawable.hs_radio_off);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.app_view_out);
    }

    /** 视图形态 → 官方中文名。 */
    public static String styleName(int mode) {
        switch (mode) {
            case AppListAdapter.MODE_STAR:
                return "行星视图";
            case AppListAdapter.MODE_GRID:
                return "宫格视图";
            default:
                return "列表视图";
        }
    }
}
