package com.trae.ticlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * ColorOS 手表：运动（复刻 HeySports 主界面）。
 *
 * 官方 MainActivity 用 ViewPager2，page0 即运动类型列表（打开应用默认页），
 * 本页按官方 res/layout/main_sport_item_layout.xml 的结构与 dimens 还原该列表：
 * 深灰胶囊项 + 亮绿圆图标（内置图标 tint 黑）+ 16sp 白色运动名。
 *
 * 交互：点击运动项 → 运动准备页（3 秒倒计时）→ 运动记录页 → 运动结束页。
 *
 * 素材与文案全部取自解包后的 HeySports APK，不引用任何 com.oppo / com.heytap 类，
 * 也不使用其它来源素材；页面不做自绘，只使用标准 Android API（minSdk 28）。
 */
public class SportActivity extends Activity {

    /** 列表项（顺序即官方默认运动类型顺序）。 */
    private static final int[] ITEM_IDS = {
            R.id.sport_item_walk,
            R.id.sport_item_ride,
            R.id.sport_item_tennis,
            R.id.sport_item_badminton,
            R.id.sport_item_basketball,
            R.id.sport_item_climb,
            R.id.sport_item_elliptical,
            R.id.sport_item_yoga,
    };

    /** 与 ITEM_IDS 一一对应的官方中文运动名。 */
    private static final String[] ITEM_NAMES = {
            "户外健走",
            "户外骑行",
            "网球",
            "羽毛球",
            "篮球",
            "登山",
            "椭圆机",
            "瑜伽",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport);

        findViewById(R.id.sport_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        for (int i = 0; i < ITEM_IDS.length; i++) {
            final String name = ITEM_NAMES[i];
            findViewById(ITEM_IDS[i]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(final View v) {
                    pressFeedback(v, name);
                }
            });
        }
    }

    /** 复刻官方 ZoomItemView 的按压手感，回弹后进入运动准备页。 */
    private void pressFeedback(final View v, final String sportName) {
        v.animate()
                .scaleX(0.94f).scaleY(0.94f)
                .setDuration(80)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        Intent i = new Intent(SportActivity.this, SportPrepareActivity.class);
                        i.putExtra(SportPrepareActivity.EXTRA_SPORT_NAME, sportName);
                        startActivity(i);
                    }
                })
                .start();
    }
}
