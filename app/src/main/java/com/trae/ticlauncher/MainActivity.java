package com.trae.ticlauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.CurvingLayoutCallback;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 启动器主窗口（既是默认桌面）。
 *
 * 复刻 ColorOS Watch 交互：
 *  · 亮屏 / 从别处按 Home 回来 —— 显示表盘首页
 *  · 在表盘首页按 Home（皇冠键）—— 打开应用列表
 *  · 在应用列表按 Home —— 回到表盘首页
 *  · 表盘首页长按 —— 更换表盘
 *  · 从顶部下滑 —— 控制中心
 *  · 应用列表支持「列表」与「星图」两种形态
 */
public class MainActivity extends Activity {

    private static final String TAG = "Launcher";
    /** 由设置页等入口请求直接展开控制中心。 */
    public static final String EXTRA_SHOW_QUICK = "show_quick";
    private static final int REQ_BODY_SENSORS = 100;
    /** Home 键触发的 onPause 与 onNewIntent 间隔通常只有几十毫秒，以此判定按键来源。 */
    private static final long HOME_TOGGLE_WINDOW_MS = 500L;

    private View mHomeScreen;
    private View mListScreen;
    private HomePager mPager;
    private HealthPanelView mHealthPanel;
    private WatchFaceView mWatchFace;
    private WearableRecyclerView mRecyclerView;
    private RadialAppView mRadial;

    private AppListAdapter mAdapter;

    private boolean mShowingList = false;
    /** 本 Activity 是否处于前台；用于区分「在首页按 Home」与「从别处按 Home 回来」。 */
    private boolean mForeground = false;

    private float mDownX, mDownY;
    private boolean mAtTop = true;
    private boolean mAtBottom = true;
    private String mAppsSignature;

    /**
     * 应用列表缓存。查询包信息 + 加载每个应用图标在主线程上很慢，
     * 而 onResume 每次都会调用 refreshApps()，若不缓存，从任何应用返回桌面
     * 都会卡一下、甚至掉帧露灰底。这里只在「安装/卸载/更新」时才重建。
     */
    private List<AppEntry> mCachedApps;
    private boolean mAppsDirty = true;
    private BroadcastReceiver mPackageReceiver;
    private long mPauseAt;

    // ---- 下拉控制中心（跟手 + 渐进毛玻璃） ----
    private View mQuickPanel;
    private ImageView mQuickBlur;
    private QuickPanelController mQuickController;
    private Bitmap mBlurBitmap;
    private boolean mPanelOpen = false;
    private boolean mQuickDragging = false;
    /** true=从底部上滑打开（面板从下往上），false=从顶部下拉打开。 */
    private boolean mPanelFromBottom = false;
    private float mDragSign = 1f;
    private float mPanelProgress = 0f;
    private float mDragStartDy = 0f;
    private float mDragStartProgress = 0f;
    private int mPanelHeight = 0;
    private int mTouchSlop = 0;
    /** 本次手势是否禁止上下拉出控制中心（星环页为 true）。 */
    private boolean mPullBlocked = false;
    /**
     * 「横向滑动保护」截止时刻。这台手表的系统会把「左→右」滑动识别成返回，
     * 直接结束桌面并露出上一个应用；若在保护窗口内桌面被结束，就自动把自己拉回来。
     */
    private long mGestureGuardUntil = 0L;
    /** 是否是我们自己主动打开了别的应用（此时被 stop 属正常，不要拉回）。 */
    private boolean mLaunchingApp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHomeScreen = findViewById(R.id.home_screen);
        mListScreen = findViewById(R.id.list_screen);
        if (mListScreen != null) {
            // 应用中心长按空白处 → 官方「应用视图」弹窗（切换 行星 / 宫格 / 列表）
            mListScreen.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    open(AppViewStyleActivity.class);
                    return true;
                }
            });
        }

        // 首页三页：负一屏（健康）/ 表盘主页 / 卡片页
        mPager = findViewById(R.id.home_pager);
        mHealthPanel = findViewById(R.id.health_panel);
        if (mPager != null) {
            mPager.setPage(HomePager.PAGE_WATCH_FACE, false);
            mPager.setOnGestureListener(new Runnable() {
                @Override
                public void run() {
                    mGestureGuardUntil = SystemClock.uptimeMillis() + 900L;
                }
            });
            mPager.setOnPageChangedListener(new HomePager.OnPageChangedListener() {
                @Override
                public void onPageChanged(int page) {
                    if (page == 0) {
                        ensureBodySensors();
                    }
                    updatePageActivity();
                }
            });
        }
        updatePageActivity();

        // 表盘主页：点按进入应用列表，长按换表盘
        mWatchFace = findViewById(R.id.watch_face);
        if (mWatchFace != null) {
            mWatchFace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showList(true);
                }
            });
            mWatchFace.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    open(WatchFaceActivity.class);
                    return true;
                }
            });
        }

        CardPanelView cards = findViewById(R.id.card_panel);
        if (cards != null) {
            cards.setOnCardClickListener(new CardPanelView.OnCardClickListener() {
                @Override
                public void onCardClick(int index) {
                    launchCard(index);
                }
            });
        }

        // 列表形态
        mRecyclerView = findViewById(R.id.app_list);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setEdgeItemsCenteringEnabled(true);
        WearableLinearLayoutManager lm = new WearableLinearLayoutManager(this);
        lm.setLayoutCallback(new CurvingLayoutCallback(this));
        mRecyclerView.setLayoutManager(lm);
        mAdapter = new AppListAdapter(this, new ArrayList<AppEntry>());
        mRecyclerView.setAdapter(mAdapter);

        // 星环形态（六边形星图，可拖动平移）
        mRadial = findViewById(R.id.radial_list);
        mRadial.bind(null, mAppClick, mAppLongClick);
        registerPackageReceiver();

        int dp = Math.round(getResources().getDisplayMetrics().density * 34);
        mRecyclerView.setPadding(0, dp, 0, Math.round(dp * 1.35f));
        mRecyclerView.setClipToPadding(false);

        // 下拉控制中心：内嵌浮层，面板跟手下滑，背景渐进毛玻璃
        mQuickPanel = findViewById(R.id.quick_panel);
        mQuickBlur = findViewById(R.id.quick_blur);
        mQuickController = new QuickPanelController(this, mQuickPanel, new Runnable() {
            @Override
            public void run() {
                settleQuickPanel(false);
            }
        });
        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHOW_QUICK, false)) {
            getIntent().removeExtra(EXTRA_SHOW_QUICK);
            mQuickPanel.post(new Runnable() {
                @Override
                public void run() {
                    settleQuickPanel(true);
                }
            });
        }

        applyListMode();
    }

    // ---------- 两屏切换 ----------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 来自设置页等入口：直接展开控制中心
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_QUICK, false)) {
            intent.removeExtra(EXTRA_SHOW_QUICK);
            setIntent(intent);
            settleQuickPanel(true);
            return;
        }
        // 控制中心开着时按 Home：先收起，再回表盘首页
        if (mPanelOpen || mPanelProgress > 0f) {
            settleQuickPanel(false);
            showHome(false);
            return;
        }
        // 系统按下 Home 键时的顺序是 onPause -> onNewIntent -> onResume，
        // 因此用「距离上次 onPause 的间隔」判断这次 Home 是不是在本启动器上按的。
        boolean wasOnTop = mForeground
                || (SystemClock.uptimeMillis() - mPauseAt) < HOME_TOGGLE_WINDOW_MS;
        Log.d(TAG, "onNewIntent wasOnTop=" + wasOnTop + " showingList=" + mShowingList);
        if (wasOnTop) {
            if (mShowingList) {
                // 从应用列表按 Home → 回表盘
                showHome(true);
            } else {
                // 只有在表盘主页时才进应用列表；在负一屏/卡片页时先滚回表盘
                if (mPager != null && mPager.getPage() != HomePager.PAGE_WATCH_FACE) {
                    mPager.setPage(HomePager.PAGE_WATCH_FACE, true);
                } else {
                    showList(true);
                }
            }
        } else {
            // 从其它页面回来：回到表盘主页
            showHome(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mForeground = true;
        mLaunchingApp = false;
        updatePageActivity();
        Log.d(TAG, "onResume showingList=" + mShowingList);
        refreshApps();
        if (mQuickController != null) {
            mQuickController.onResume();
        }
    }

    /**
     * 横向滑动被系统误判成返回时，桌面会被直接结束并露出上一个应用。
     * 这里在保护窗口内把自己重新拉回前台，让「向右滑」不至于把桌面弄丢。
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (mLaunchingApp || SystemClock.uptimeMillis() >= mGestureGuardUntil) {
            return;
        }
        mGestureGuardUntil = 0L;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(home);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mForeground = false;
        mPauseAt = SystemClock.uptimeMillis();
        // 桌面不在前台（切到别的应用 / 息屏）：立刻停掉所有传感器与定时刷新
        updatePageActivity();
        if (mQuickController != null) {
            mQuickController.onPause();
        }
    }

    /**
     * 屏蔽「返回上一页」：返回键只负责收起控制中心，不再回退到上一页，
     * 更不会让桌面退出露出上一个应用。页面切换一律走 Home 键。
     */
    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed list=" + mShowingList
                + " panelOpen=" + mPanelOpen + " progress=" + mPanelProgress);
        if (mPanelOpen || mPanelProgress > 0f) {
            settleQuickPanel(false);
        }
    }

    /** 当前是否正显示「星环」形态的应用列表。 */
    private boolean isStarPage() {
        return mShowingList && listMode() == AppListAdapter.MODE_STAR;
    }

    /**
     * 统一决定「哪一页可以干活」。
     *
     * 只有当桌面在前台（亮屏、没被别的应用盖住）且该页正显示时，才允许它跑传感器和定时刷新；
     * 其余情况全部停掉。这不会削减任何功能或数据：
     *  · 步数是硬件累计计数器，停掉期间照常计数，恢复时立刻读到最新值；
     *  · 心跳卡片只是实时概览，重新可见时马上重新采样；
     *  · 时间在 onDraw 里现取，不依赖后台定时器。
     */
    private void updatePageActivity() {
        boolean homeVisible = mForeground && !mShowingList;
        int page = mPager == null ? HomePager.PAGE_WATCH_FACE : mPager.getPage();
        if (mWatchFace != null) {
            mWatchFace.setActive(homeVisible && page == HomePager.PAGE_WATCH_FACE);
        }
        if (mHealthPanel != null) {
            mHealthPanel.setActive(homeVisible && page == 0);
        }
    }

    /** 负一屏的心率卡片需要 BODY_SENSORS，第一次滑到该页时申请一次。 */
    private void ensureBodySensors() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY_SENSORS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 授权后重新注册健康面板传感器（步数 + 心率）
        if (mHealthPanel != null) {
            mHealthPanel.setActive(false);
            updatePageActivity();
        }
    }

    private void showHome(boolean animate) {
        if (!mShowingList && mHomeScreen.getVisibility() == View.VISIBLE) {
            return;
        }
        // 回表盘时总是停在中间的「表盘主页」
        if (mPager != null) {
            mPager.setPage(HomePager.PAGE_WATCH_FACE, false);
        }
        mShowingList = false;
        updatePageActivity();
        if (!animate) {
            mHomeScreen.setVisibility(View.VISIBLE);
            mHomeScreen.setAlpha(1f);
            mHomeScreen.setScaleX(1f);
            mHomeScreen.setScaleY(1f);
            mListScreen.setVisibility(View.GONE);
            return;
        }
        mHomeScreen.setVisibility(View.VISIBLE);
        mHomeScreen.setAlpha(0f);
        mHomeScreen.setScaleX(1.06f);
        mHomeScreen.setScaleY(1.06f);
        mHomeScreen.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        mListScreen.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
                .setDuration(170).setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mListScreen.setVisibility(View.GONE);
                    }
                }).start();
    }

    private void showList(boolean animate) {
        if (mShowingList) {
            return;
        }
        mShowingList = true;
        // 应用列表打开期间首页不可见，两页的传感器与定时刷新全部停掉
        updatePageActivity();
        if (!animate) {
            mListScreen.setVisibility(View.VISIBLE);
            mListScreen.setAlpha(1f);
            mListScreen.setScaleX(1f);
            mListScreen.setScaleY(1f);
            mHomeScreen.setVisibility(View.GONE);
            onListShown();
            return;
        }
        mListScreen.setVisibility(View.VISIBLE);
        mListScreen.setAlpha(0f);
        mListScreen.setScaleX(0.92f);
        mListScreen.setScaleY(0.92f);
        mListScreen.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(210).setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        onListShown();
                    }
                }).start();
        mHomeScreen.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f)
                .setDuration(170).setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mHomeScreen.setVisibility(View.GONE);
                    }
                }).start();
    }

    /** 应用列表出现后，触发当前形态的入场动效。 */
    private void onListShown() {
        if (listMode() == AppListAdapter.MODE_STAR) {
            if (mRadial != null) {
                mRadial.playEnter();
            }
        } else if (mAdapter != null) {
            mAdapter.replayEnter();
            mRecyclerView.scrollToPosition(0);
        }
    }

    // ---------- 应用列表形态 ----------

    private int listMode() {
        return getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getInt(SettingsActivity.KEY_LIST_STYLE, AppListAdapter.MODE_LIST);
    }

    private void applyListMode() {
        int mode = listMode();
        boolean star = mode == AppListAdapter.MODE_STAR;
        int dp = Math.round(getResources().getDisplayMetrics().density);
        if (mRecyclerView != null) {
            mRecyclerView.setVisibility(star ? View.GONE : View.VISIBLE);
            RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
            if (mode == AppListAdapter.MODE_GRID) {
                if (!(lm instanceof GridLayoutManager)) {
                    mRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
                }
                mRecyclerView.setPadding(dp * 10, dp * 14, dp * 10, dp * 14);
            } else {
                if (!(lm instanceof WearableLinearLayoutManager)) {
                    WearableLinearLayoutManager l = new WearableLinearLayoutManager(this);
                    l.setLayoutCallback(new CurvingLayoutCallback(this));
                    mRecyclerView.setLayoutManager(l);
                }
                mRecyclerView.setPadding(0, dp * 34, 0, Math.round(dp * 34 * 1.35f));
            }
            mRecyclerView.setClipToPadding(false);
            if (mAdapter != null) {
                mAdapter.setMode(mode);
            }
        }
        if (mRadial != null) {
            mRadial.setVisibility(star ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshApps() {
        if (!mAppsDirty && mCachedApps != null) {
            // 包列表没变：直接沿用缓存，onResume 不再做任何重活
            applyListMode();
            return;
        }
        List<AppEntry> apps = loadApps();
        mCachedApps = apps;
        mAppsDirty = false;
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) {
            sb.append(e.label).append('|');
        }
        mAppsSignature = sb.toString();
        if (mAdapter != null) {
            mAdapter.updateApps(apps);
        }
        if (mRadial != null) {
            mRadial.bind(apps, mAppClick, mAppLongClick);
        }
        applyListMode();
        if (mShowingList) {
            onListShown();
        }
    }

    /** 安装 / 卸载 / 更新应用时把列表标记为脏，下次回到桌面才会重建。 */
    private void registerPackageReceiver() {
        mPackageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                mAppsDirty = true;
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        try {
            registerReceiver(mPackageReceiver, f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPackageReceiver != null) {
            try {
                unregisterReceiver(mPackageReceiver);
            } catch (Exception ignored) {
            }
            mPackageReceiver = null;
        }
    }

    private final View.OnClickListener mAppClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Object tag = v.getTag();
            if (!(tag instanceof AppEntry)) {
                return;
            }
            AppEntry e = (AppEntry) tag;
            if (e.type == AppEntry.TYPE_APP) {
                launchEntry(e);
            } else if (e.type == AppEntry.TYPE_WATCHFACE) {
                open(WatchFaceActivity.class);
            } else {
                open(SettingsActivity.class);
            }
        }
    };

    private final View.OnLongClickListener mAppLongClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Object tag = v.getTag();
            if (!(tag instanceof AppEntry)) {
                return false;
            }
            AppEntry e = (AppEntry) tag;
            if (e.type != AppEntry.TYPE_APP || e.component == null) {
                return false;
            }
            Intent i = new Intent(MainActivity.this, AppMenuActivity.class);
            i.putExtra(AppMenuActivity.EXTRA_PACKAGE, e.component.getPackageName());
            i.putExtra(AppMenuActivity.EXTRA_CLASS, e.component.getClassName());
            i.putExtra(AppMenuActivity.EXTRA_LABEL, e.label);
            try {
                mLaunchingApp = true;
                startActivity(i);
            } catch (Exception ignored) {
                mLaunchingApp = false;
            }
            return true;
        }
    };

    private void launchEntry(AppEntry e) {
        if (e.component == null) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(e.component);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            mLaunchingApp = true;
            startActivity(i);
        } catch (Exception e2) {
            mLaunchingApp = false;
        }
    }

    private void open(Class<?> cls) {
        try {
            mLaunchingApp = true;
            startActivity(new Intent(this, cls));
        } catch (Exception ignored) {
            mLaunchingApp = false;
        }
    }

    // ---------- 手势 ----------

    /**
     * 手势：
     *  · 顶部下拉 → 打开控制中心（QuickPanel）
     *  · 底部上滑 → 打开 Wear OS 系统通知流（EXPAND_TOP_NOTIFICATION）
     *  · 已展开的控制中心反向滑动收起；松手超过 40% 吸附展开，否则弹回。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mQuickDragging = false;
                // 星环页面禁止上下拉出控制中心 / 通知流，避免转动星环时误触
                mPullBlocked = isStarPage();
                mAtTop = !mShowingList
                        || mRecyclerView == null
                        || mRecyclerView.getVisibility() != View.VISIBLE
                        || !mRecyclerView.canScrollVertically(-1);
                mAtBottom = !mShowingList
                        || mRecyclerView == null
                        || mRecyclerView.getVisibility() != View.VISIBLE
                        || !mRecyclerView.canScrollVertically(1);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - mDownX;
                float dy = ev.getY() - mDownY;
                // 只要出现「向右」的横向滑动，就打开保护窗口：
                // 这台手表的系统会把这种滑动当返回，直接结束桌面。
                if (dx > mTouchSlop && Math.abs(dx) > Math.abs(dy)) {
                    mGestureGuardUntil = SystemClock.uptimeMillis() + 900L;
                }
                boolean vertical = Math.abs(dy) > Math.abs(dx) * 1.2f;
                if (!mQuickDragging && vertical && Math.abs(dy) > mTouchSlop) {
                    if (!mPanelOpen) {
                        if (mPullBlocked) {
                            // 星环页：上下拉不做任何事
                        } else if (dy > 0 && mAtTop) {
                            // 顶部下拉 → 控制中心
                            startQuickDrag(true, dy, false);
                            mQuickDragging = true;
                        } else if (dy < 0 && mAtBottom) {
                            // 底部上滑 → 系统通知流
                            openNotificationStream();
                            mQuickDragging = true; // 让后续 MOVE/UP 被吞掉，不触发子 View
                        }
                    } else {
                        // 已展开控制中心：反方向滑动收起
                        boolean closing = mPanelFromBottom ? dy > 0 : dy < 0;
                        if (closing) {
                            startQuickDrag(false, dy, mPanelFromBottom);
                            mQuickDragging = true;
                        }
                    }
                }
                if (mQuickDragging) {
                    // 通知流模式：直接吞掉手势，不做拖动
                    if (!mPanelOpen && !mQuickPanel.isShown()) {
                        return true;
                    }
                    setPanelProgress(mDragStartProgress
                            + mDragSign * (dy - mDragStartDy) / (float) panelHeight());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mQuickDragging) {
                    mQuickDragging = false;
                    if (mPanelOpen || mQuickPanel.isShown()) {
                        settleQuickPanel(mPanelProgress > 0.4f);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 打开 Wear OS 系统通知流。
     * 发送 Intent com.google.android.clockwork.home.EXPAND_TOP_NOTIFICATION，
     * 由 com.google.android.wearable.app 的 HomeActivity2 接收并展示通知流。
     */
    private void openNotificationStream() {
        try {
            Intent i = new Intent("com.google.android.clockwork.home.EXPAND_TOP_NOTIFICATION");
            i.setComponent(new ComponentName(
                    "com.google.android.wearable.app",
                    "com.google.android.clockwork.home2.activity.HomeActivity2"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "openNotificationStream failed", e);
        }
    }

    // ---------- 控制中心跟手拖拽 ----------

    private int panelHeight() {
        if (mPanelHeight <= 0) {
            mPanelHeight = mQuickPanel != null && mQuickPanel.getHeight() > 0
                    ? mQuickPanel.getHeight()
                    : getResources().getDisplayMetrics().heightPixels;
        }
        return mPanelHeight;
    }

    private void startQuickDrag(boolean opening, float dy, boolean fromBottom) {
        mPanelHeight = 0;
        mDragStartDy = dy;
        mDragStartProgress = mPanelProgress;
        mDragSign = fromBottom ? -1f : 1f;
        // 接管手势前先给下层控件发一个 CANCEL，避免它残留按下态并误触发点击
        cancelChildTouch();
        if (opening) {
            mPanelFromBottom = fromBottom;
            captureBlurSnapshot();
        }
        mQuickPanel.setVisibility(View.VISIBLE);
        mQuickBlur.setVisibility(View.VISIBLE);
    }

    private void cancelChildTouch() {
        MotionEvent cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    /** 按当前进度摆放面板与毛玻璃层：面板前沿 = 手指位置，毛玻璃随覆盖比例淡入。 */
    private void setPanelProgress(float p) {
        mPanelProgress = Math.max(0f, Math.min(1f, p));
        float dir = mPanelFromBottom ? 1f : -1f;
        mQuickPanel.setTranslationY(dir * panelHeight() * (1f - mPanelProgress));
        mQuickBlur.setAlpha(mPanelProgress);
    }

    private void settleQuickPanel(final boolean open) {
        mPanelOpen = open;
        if (open) {
            mPanelHeight = 0;
            if (mBlurBitmap == null) {
                captureBlurSnapshot();
            }
            mQuickPanel.setVisibility(View.VISIBLE);
            mQuickBlur.setVisibility(View.VISIBLE);
        }
        ValueAnimator va = ValueAnimator.ofFloat(mPanelProgress, open ? 1f : 0f);
        va.setDuration(Math.max(90L, (long) (Math.abs((open ? 1f : 0f) - mPanelProgress) * 220f)));
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                setPanelProgress((float) a.getAnimatedValue());
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (open) {
                    if (mQuickController != null) {
                        mQuickController.refresh();
                    }
                } else {
                    mQuickPanel.setVisibility(View.GONE);
                    mQuickBlur.setVisibility(View.GONE);
                    mQuickBlur.setImageBitmap(null);
                    if (mBlurBitmap != null) {
                        mBlurBitmap.recycle();
                        mBlurBitmap = null;
                    }
                }
            }
        });
        va.start();
    }

    /**
     * 对当前画面做一次降采样再放大的模糊快照，作为毛玻璃底图。
     * 只在每次拉开面板时截一次，拖动过程中只改透明度，保证跟手不掉帧。
     */
    private void captureBlurSnapshot() {
        View root = findViewById(R.id.main_root);
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (mBlurBitmap != null) {
            mBlurBitmap.recycle();
            mBlurBitmap = null;
        }
        int sw = Math.max(1, Math.round(w / 14f));
        int sh = Math.max(1, Math.round(h / 14f));
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        sc.scale(sw / (float) w, sh / (float) h);
        root.draw(sc);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        oc.drawBitmap(small, null, new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        small.recycle();
        mBlurBitmap = out;
        mQuickBlur.setImageBitmap(out);
        mQuickBlur.setAlpha(mPanelProgress);
    }

    // ---------- 数据 ----------

    /**
     * 卡片页四张快捷卡 → Intent Action 映射（隐式启动 ColorOS 复刻应用）。
     * 顺序：运动 / 心率 / 音乐 / 天气
     */
    private static final String[] CARD_ACTIONS = {
            "heytap.wearable.intent.action.health.START_DAILY",    // SportActivity
            "heytap.wearable.intent.action.health.START_HEARTRATE",// HeartRateActivity
            null,                                                   // 音乐 → fallback 搜索播放器
            "heytap.wearable.intent.action.START_WEATHER",         // WeatherActivity
    };

    /** 卡片页点击：优先按 Action 启动我们自己的复刻应用 → 再 fallback 到应用列表。 */
    private void launchCard(int index) {
        if (index < 0 || index >= CARD_ACTIONS.length) return;

        PackageManager pm = getPackageManager();

        // 1) 按 Action 找
        String action = CARD_ACTIONS[index];
        if (action != null) {
            Intent i = new Intent(action);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            // 关键：把解析范围限定在本应用。
            // 官方 OPPO 健康应用声明了同名 HeyTap Action，但它们按 API 30 编译，
            // 在 API 28 的 TicWatch 上必然崩溃；不限定包名时卡片可能被它们抢占 → 灰屏。
            i.setPackage(getPackageName());
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (pm.resolveActivity(i, 0) != null) {
                mLaunchingApp = true;
                startActivity(i);
                return;
            }
        }

        // 2) 音乐：找任意带 PLAY/PAUSE 的媒体应用
        if (index == 2) {
            Intent playSearch = new Intent(Intent.ACTION_MAIN);
            playSearch.addCategory(Intent.CATEGORY_APP_MUSIC);
            for (ResolveInfo ri : pm.queryIntentActivities(playSearch, 0)) {
                if (getPackageName().equals(ri.activityInfo.packageName)) continue;
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try { mLaunchingApp = true; startActivity(i); return; }
                catch (Exception ignored) {}
            }
        }

        // 3) 兜底：打开应用列表
        showList(true);
    }

    /**
     * 本应用内各"复刻应用"在桌面显示的名称。
     * 这些 Activity 的 android:label 都继承自 application，这里按类名区分，否则桌面会出现
     * 多个同名条目、也找不到"运动"入口。
     */
    private static String selfAppLabel(String cls) {
        if (cls.endsWith(".SportActivity")) return "运动";
        if (cls.endsWith(".HeartRateActivity")) return "心率";
        if (cls.endsWith(".SleepActivity")) return "睡眠";
        if (cls.endsWith(".OxygenActivity")) return "血氧";
        if (cls.endsWith(".WeatherActivity")) return "天气";
        if (cls.endsWith(".FlashlightActivity")) return "手电筒";
        if (cls.endsWith(".CompassActivity")) return "指南针";
        if (cls.endsWith(".PowerSaveActivity")) return "电池";
        if (cls.endsWith(".SmartModeActivity")) return "轻智能";
        if (cls.endsWith(".RelaxActivity")) return "呼吸";
        if (cls.endsWith(".StressActivity")) return "压力";
        if (cls.endsWith(".ClockActivity")) return "时钟";
        if (cls.endsWith(".CalendarActivity")) return "日历";
        if (cls.endsWith(".WorkoutActivity")) return "运动记录";
        return null;
    }

    /** 与 {@link #selfAppLabel} 对应的桌面图标。 */
    private static int selfAppIcon(String cls) {
        if (cls.endsWith(".SportActivity")) return R.drawable.ic_app_sports;
        if (cls.endsWith(".HeartRateActivity")) return R.drawable.ic_app_heartrate;
        if (cls.endsWith(".SleepActivity")) return R.drawable.ic_app_sleep;
        if (cls.endsWith(".OxygenActivity")) return R.drawable.ic_app_oxygen;
        if (cls.endsWith(".WeatherActivity")) return R.drawable.ic_app_weather;
        if (cls.endsWith(".FlashlightActivity")) return R.drawable.ic_flashlight;
        if (cls.endsWith(".CompassActivity")) return R.drawable.ic_app_compass;
        if (cls.endsWith(".PowerSaveActivity")) return R.drawable.ic_battery_100;
        if (cls.endsWith(".SmartModeActivity")) return R.drawable.ic_app_settings;
        if (cls.endsWith(".RelaxActivity")) return R.drawable.ic_app_relax;
        if (cls.endsWith(".StressActivity")) return R.drawable.ic_app_stress;
        if (cls.endsWith(".ClockActivity")) return R.drawable.ic_app_alarmclock;
        if (cls.endsWith(".CalendarActivity")) return R.drawable.ic_app_daily;
        if (cls.endsWith(".WorkoutActivity")) return R.drawable.ic_app_stopwatch;
        return R.drawable.ic_app_empty;
    }

    /** 读取所有可启动应用（含本应用内复刻的各"应用"，仅排除桌面自身） */
    private List<AppEntry> loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<AppEntry> list = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            AppEntry e = new AppEntry();
            if (getPackageName().equals(pkg)) {
                // 本应用：排除桌面自身，其余复刻应用用各自的名称 / 图标
                if (MainActivity.class.getName().equals(cls)) {
                    continue;
                }
                String selfLabel = selfAppLabel(cls);
                if (selfLabel == null) {
                    continue;
                }
                e.label = selfLabel;
                e.icon = getDrawable(selfAppIcon(cls));
            } else {
                e.label = ri.loadLabel(pm).toString();
                e.icon = ri.loadIcon(pm);
            }
            e.component = new ComponentName(pkg, cls);
            list.add(e);
        }
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        // 末尾追加两个固定入口
        AppEntry face = AppEntry.special(AppEntry.TYPE_WATCHFACE, "表盘");
        face.icon = getDrawable(R.drawable.ic_watchface);
        list.add(face);
        AppEntry setting = AppEntry.special(AppEntry.TYPE_SETTINGS, "设置");
        setting.icon = getDrawable(R.drawable.ic_app_settings);
        list.add(setting);
        return list;
    }
}
