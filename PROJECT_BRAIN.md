# 项目大脑（TicWatch 云端开发）

> 这份文件是 TicLauncher 项目的**单文件对接文档**，用于把项目上下文完整交接给任意 AI 助手。
> 只描述目标、结构、接口清单与规则，不贴大段实现代码。
>
> 关联文档：[README.md](README.md)（构建与部署）· [DEV_NOTES.md](DEV_NOTES.md)（踩坑与架构细节）· [TODO.md](TODO.md)（待办）· [.trae/rules/project-rules.md](.trae/rules/project-rules.md)（硬性规则）
>
> 最后更新：2026-10-11 · 对应仓库 `https://github.com/15816563131/ticlauncher`（main 分支）

---

## 1. 项目目标与硬件事实

### 1.1 项目目标

在 **TicWatch Pro（初代）** 上**像素级复刻 OPPO Watch（ColorOS）手表端体验**。
纯原生 Android 实现，不用 AppCompat / Compose；**素材 100% 取自解包后的 OPPO Watch X 官方 ROM**。
最终产物必须**完全伪装**，不能留下任何 TicWatch 痕迹。

### 1.2 硬件与系统事实

| 项 | 值 |
|---|---|
| 设备 | TicWatch Pro（初代，Mobvoi） |
| 系统 | Wear OS 2.3 / Android 9，**API 28** |
| 屏幕 | 400 × 400 圆屏，density 280（≈228.6dp） |
| 设备连接 | 无线 adb：`adb connect <watch-ip>:5555`，手表休眠后掉线需重连 |
| 心率传感器 | **PixArt PAH8011**（"Heart Rate PPG"），暴露为标准 `TYPE_HEART_RATE` |
| 步数传感器 | `TYPE_STEP_COUNTER`（硬件累计计数器，停掉监听不会丢数据） |
| 其他传感器 | 旋转矢量 / 重力 / 线性加速度 / 磁力计 / 陀螺仪 / `TYPE_STEP_DETECTOR` / `low_latency_offbody_detect` / Mobvoi 私有 `mobvoi_activity_recognizer`、`mobvoi_activity_data` / Google `com.google.wear.sensor.ppg` |
| **缺失** | `TYPE_PRESSURE`（**无气压计**）→ 海拔相关功能已整体移除 |

### 1.3 底层红线（绝对不要做）

- **绝不动系统底层引导文件**：`xbl` / `preloader` / bootloader / 分区表一律不碰，只在应用层开发。
- **绝对不要尝试移植 OPPO 官方 APK 到本机**。官方 APK 是 `minSdkVersion=30` 编译的，R8 会把 SDK 版本判断常量折叠掉，调用链不收敛（`SdkExtensions` → `registerActivityLifecycleCallbacks` → `View.saveAttributeDataForStyleable`，到第三个无法集中修复）。
- **不要占用 C 盘空间**：项目文件、SDK、缓存、临时产物都不放 C 盘。
- **禁止使用 API 29 及以上的方法 / 类**（目标设备只到 API 28）。

### 1.4 素材来源

| 路径 | 内容 |
|---|---|
| `D:\111\oppoicons` | 从官方 APK 提取的 drawable |
| `D:\111\oppowatch` | 解包产物：150 个官方 APK + `analysis/` 下的布局 / 资源映射 / 表盘 `main.js` |
| `D:\111\tools\apktool.jar` | apktool 2.9.3 |
| `D:\111\oppowatch\OPPO_Watch_X_解包成果清单.md` | 解包成果总账 |

---

## 2. 核心工程与目录结构

### 2.1 仓库根目录

```
ticlauncher/
├── README.md              项目简介 / 构建 / 手机端安装
├── DEV_NOTES.md           开发记忆：踩坑、硬件、架构、省电设计
├── TODO.md                待办清单
├── PROJECT_BRAIN.md       本文件
├── .trae/rules/           项目规则（Trae 自动加载）
├── .github/workflows/     build.yml —— 推送 main 自动编译并发布 latest
├── .gitattributes         *.sh 强制 LF
├── .gitignore
├── tools/
│   ├── autosync.ps1       电脑侧每 10 分钟 pull/commit/push
│   └── phone-install.sh   手机 Termux 一键下载并安装 APK
├── build.gradle           根构建脚本
├── settings.gradle        仓库源（阿里云镜像优先）
├── gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/trae/ticlauncher/   ← 40 个类
        └── res/
            ├── layout/                  ← 33 个布局
            ├── drawable/                ← 官方素材 + 少量自绘 shape
            ├── anim/                    ← app_view_in / app_view_out / task_anim_interpolator
            ├── font/oplusans_watch.ttf  ← 官方 OPPO 字体
            └── values/                  ← colors / strings / styles
```

### 2.2 构建参数

| 项 | 值 |
|---|---|
| 语言 | Java 17 |
| 构建 | AGP **8.7.3** · Gradle **8.14.1** · JDK 17 |
| SDK | compileSdk **34** · minSdk **28** · targetSdk **30** |
| 依赖 | `androidx.core` / `androidx.recyclerview` / `androidx.wear` / `androidx.constraintlayout` |
| 本地编译 | `D:\111\gradle-8.14.1\bin\gradle.bat assembleDebug --gradle-user-home D:\111\gradlehome` |
| 产物 | `app/build/outputs/apk/debug/app-debug.apk` |
| 云端 | 推送 `main` → GitHub Actions 自动编译 → 发布 `latest`，下载地址 `https://github.com/15816563131/ticlauncher/releases/download/latest/app-debug.apk` |

### 2.3 主界面布局层序（`res/layout/activity_main.xml`）

```
FrameLayout(main_root)
 ├─ include home_screen   → screen_home.xml   表盘首页（亮屏即见）
 ├─ include list_screen   → screen_applist.xml 应用列表（仅能从表盘页按 Home 进入）
 ├─ ImageView quick_blur   下拉控制中心的毛玻璃层（随下拉比例淡入）
 └─ FrameLayout quick_panel → activity_quick.xml 控制中心面板（跟手下滑）
```

`screen_home.xml` 内是 `HomePager`，横向三页，默认停在中间：

```
[0] HealthPanelView 负一屏（健康卡片）
[1] WatchFaceView   表盘主页
[2] CardPanelView   卡片页
```

### 2.4 res/layout 清单（33 个）

**主界面**：`activity_main` · `screen_home` · `screen_applist` · `panel_health` · `panel_cards` · `activity_quick`

**应用相关**：`activity_app_view` · `activity_app_menu` · `item_app` · `item_app_grid`

**健康**：`activity_heartrate` · `activity_hr_record` · `activity_sport` · `activity_sport_prepare` · `activity_sport_record` · `activity_sport_end` · `activity_sleep` · `activity_oxygen` · `activity_stress` · `activity_workout` · `item_workout`

**工具**：`activity_weather` · `activity_flashlight` · `activity_compass` · `activity_power_save` · `activity_smart_mode` · `activity_relax` · `activity_clock` · `activity_calendar` · `item_calendar_event`

**表盘 / 设置**：`activity_watchface` · `item_watchface` · `activity_settings`

---

## 3. 核心类与对外接口

> 包名统一 `com.trae.ticlauncher`。以下只列**对外可见**的成员；
> 标注「内部流程」的是私有方法，仅用于说明调用链，不要直接调用。

### 3.1 启动器骨架

**`MainActivity extends Activity`** —— 入口，同时是默认桌面（`category.HOME`）
- 常量：`public static final String EXTRA_SHOW_QUICK = "show_quick"`（带此 extra 启动会直接展开控制中心）
- 公开：`onBackPressed()`（屏蔽返回；只用于收起控制中心）、`dispatchTouchEvent(MotionEvent)`（手势入口）、`onRequestPermissionsResult(...)`、`onNewIntent(Intent)`
- 内部流程：`updatePageActivity()` · `showHome(boolean)` · `showList(boolean)` · `onListShown()` · `applyListMode()` · `refreshApps()` · `loadApps()` · `launchEntry(AppEntry)` · `launchCard(int)` · `open(Class)` · `openNotificationStream()` · `settleQuickPanel(boolean)` · `setPanelProgress(float)` · `captureBlurSnapshot()` · `registerPackageReceiver()` · `registerScreenReceiver()` · `isScreenOn()` · `isStarPage()`
- 数据：`List<AppEntry> mCachedApps` 缓存（仅安装/卸载/更新时重建，避免每次 onResume 卡顿）

**`HomePager extends ViewGroup`** —— 表盘首页横向分页容器
- 常量：`public static final int PAGE_WATCH_FACE = 1`
- 接口：`interface OnPageChangedListener { void onPageChanged(int page); }`
- 公开：`getPage()` · `setPage(int page, boolean animate)` · `setOnPageChangedListener(OnPageChangedListener)` · `setOnGestureListener(Runnable)` · `computeScroll()` · `onInterceptTouchEvent(MotionEvent)` · `onTouchEvent(MotionEvent)`
- 翻页规则：拖动超 1/4 屏宽 **或** 松手横向速度 > 1000 px/s 才翻页；settle 时长按距离自适应 140~320ms；首尾弹回，**不做循环回卷**

**`HomeHelper`**（final）
- `public static boolean openStockHome(Context ctx)` —— 跳回系统原生桌面（伪装逃生口）

### 3.2 首页三页

**`HealthPanelView extends LinearLayout implements SensorEventListener`** —— 负一屏健康卡片
- 公开：`setActive(boolean)`（唯一开关：只在「桌面在前台 + 本页正显示 + 屏幕亮着」时为 true）· `onSensorChanged(SensorEvent)` · `onAccuracyChanged(Sensor,int)`
- 数据源：`TYPE_STEP_COUNTER` + `TYPE_HEART_RATE`，均为 `SENSOR_DELAY_NORMAL`
- 展示项：步数（含目标进度条）/ 心率 / 消耗；数值更新走 `ValueAnimator`

**`WatchFaceView extends View`** —— 主表盘（复刻官方表盘「寰宇光尘」）
- 公开：`setActive(boolean)`（不可见时注销 `ACTION_TIME_TICK` 广播与步数监听）
- 素材：`wf_bg` `wf_mask` `wf_ticks_rt` `wf_ticks_lb` `wf_arc` `wf_point` `wf_d0`~`wf_d9`，按 466×466 官方设计稿缩放
- 全进程位图缓存（`static`），避免 Activity 重建时重复解码

**`CardPanelView extends LinearLayout`** —— 卡片页
- 接口：`interface OnCardClickListener { void onCardClick(int index); }`
- 公开：`setOnCardClickListener(OnCardClickListener)`
- 卡片顺序：运动 / 心率 / 音乐 / 天气

### 3.3 应用列表

**`AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder>`**
- 常量：`MODE_LIST = 0` · `MODE_STAR = 1` · `MODE_GRID = 2`
- 公开：`setMode(int)`（切换形态，会清入场动画并刷新）· `updateApps(List<AppEntry>)` · `replayEnter()` · `getItemCount()` · `onCreateViewHolder(...)` · `onBindViewHolder(...)`

**`RadialAppView extends ViewGroup`** —— 星图（行星视图）
- 公开：`bind(List<AppEntry> apps, OnClickListener click, OnLongClickListener longClick)` · `playEnter()` · `hideAll()` · `onInterceptTouchEvent(MotionEvent)` · `onTouchEvent(MotionEvent)` · `onGenericMotionEvent(MotionEvent)`（表冠旋转 → 纵向平移星图）
- 形态：半格偏移正方形网格取最近 N 点（中心四个等距等大，四向对称）+ 鱼眼径向映射（中心放大、外圈收紧）
- 位置记忆：拖动结束后把 `panX/panY` 存进 `SharedPreferences("radial")`

**`AppEntry`** —— 列表条目模型
- 常量：`TYPE_APP = 0` · `TYPE_WATCHFACE = 1` · `TYPE_SETTINGS = 2`
- 字段：`int type` · `String label` · `Drawable icon` · `ComponentName component`
- 公开：`static AppEntry special(int type, String label)`

**`AppViewStyleActivity extends Activity`** —— 「应用视图」弹窗（行星 / 宫格 / 列表）
- 公开：`static String styleName(int mode)`（返回中文名）· `finish()`（带出场动画）
- 写回：`SettingsActivity.PREFS` / `KEY_LIST_STYLE`

**`AppMenuActivity extends Activity`** —— 应用长按操作菜单
- 常量：`EXTRA_PACKAGE = "pkg"` · `EXTRA_CLASS = "cls"` · `EXTRA_LABEL = "label"`

### 3.4 健康类 Activity（全部 1:1 复刻 ColorOS）

| 类 | 备注 / 额外接口 |
|---|---|
| `HeartRateActivity` | 实时心率；`onSensorChanged` · `onRequestPermissionsResult` |
| `HrRecordActivity` | 心率记录/统计页（含折线图）；`onSensorChanged` · `onRequestPermissionsResult`；右滑返回 |
| `SportActivity` | 运动入口，`START_DAILY` |
| `SportPrepareActivity` | 3 秒倒计时；`EXTRA_SPORT_NAME` |
| `SportRecordActivity` | 运动记录；`EXTRA_SPORT_NAME` / `EXTRA_DISTANCE` / `EXTRA_DURATION` / `EXTRA_HEART` / `EXTRA_CALORIE`；`onSensorChanged` |
| `SportEndActivity` | 运动结束；`onBackPressed()` |
| `SleepActivity` | 睡眠 |
| `OxygenActivity` | 血氧；`onSensorChanged` |
| `StressActivity` | 压力（仅测量的 30 秒内注册心率） |
| `WorkoutActivity` | 运动记录列表 |

**`HsHrChartView extends View`** —— 心率折线图（**官方本身就是绘制，故允许 Canvas 自绘**）
- 公开：`addValue(int bpm)`（40 点滚动窗口）
- 配色取自官方 `heart_rate_chart_color = #ffe64a39`

### 3.5 工具类 Activity

`WeatherActivity` · `FlashlightActivity`（相机补光） · `CompassActivity`（`onSensorChanged`/`onAccuracyChanged`） · `PowerSaveActivity`（电池/省电/自动休眠） · `SmartModeActivity`（轻智能，极低亮度） · `RelaxActivity`（呼吸训练） · `ClockActivity`（闹钟） · `CalendarActivity`（日程，`onRequestPermissionsResult`） · `WatchFaceActivity`（表盘选择）

### 3.6 系统 / 框架类

**`SettingsActivity extends Activity`**
- 常量：`public static final String PREFS = "launcher"` · `public static final String KEY_LIST_STYLE = "applist_style"`

**`QuickPanelController`** —— 控制中心内容控制器（可绑定任意 View 根节点）
- 公开：`onResume()` · `onPause()` · `refresh()`
- 开关：飞行模式 / 勿扰 / 影院 / 省电 / 蓝牙 / WLAN / 找手机 / 手电筒 + 亮度条 + 触控锁

**`SleepModeReceiver extends BroadcastReceiver`** —— 「自动休眠」调度
- 公开：`onReceive(Context, Intent)` · `static void reschedule(Context)`
- 机制：`AlarmManager.setExactAndAllowWhileIdle` 定两个精确闹钟（开始/结束），触发后自我续排
- 开始：**切飞行模式断网 → 把蓝牙补开回来**（保通知链路）；结束：关飞行模式，网络自动恢复
- 触发点：`MainActivity.onCreate`（自愈）· `PowerSaveActivity` 开关与时间改动 · 闹钟自身

**`StepSource`**（final）—— 今日步数数据源
- 常量：`public static final int DEFAULT_GOAL = 8000`
- 字段：`int steps` · `float distance` · `int goal` · `int calorie`
- 公开：`valid()` · `static StepSource compute(Context, float totalSinceBoot)` · `static void resetBaseline(Context, float currentTotal)` · `static boolean isSensorAvailable(Context)`

**`CircleIconDrawable extends Drawable`** —— 圆形裁切 drawable
- 公开：`static Drawable of(Drawable src)` · `draw(Canvas)` · `setAlpha(int)` · `setColorFilter(ColorFilter)` · `getOpacity()` · `getOutline(Outline)`

### 3.7 与外界打通的隐式 action（**必须** `setPackage(getPackageName())`）

```
heytap.wearable.intent.action.health.START_HEARTRATE / START_DAILY / START_SLEEP / START_OXYGEN
heytap.wearable.intent.action.START_WEATHER / START_FLASHLIGHT / START_COMPASS / START_BATTERY
heytap.wearable.intent.action.START_SMART_MODE / START_RELAX / START_STRESS / START_CLOCK
heytap.wearable.intent.action.START_CALENDAR / START_WORKOUT
```

不限定包名时，同样声明了这些 action 的 OPPO 官方 APK 会抢走启动并在 API 28 上崩溃（灰屏）。

---

## 4. 当前进度与排期

### 4.1 已完成（可运行、已在真机验证）

- **主框架**：`MainActivity` 作为默认桌面；表盘首页三页横向分页；Home（皇冠键）在「首页 ↔ 应用列表」间切换；返回键被屏蔽；上滑出系统通知流；下拉出控制中心（跟手 + 渐进毛玻璃）
- **表盘页**：复刻官方表盘「寰宇光尘」，时间 / 步数 / 日期 / 刻度游标
- **负一屏**：步数 + 心率 + 消耗卡片，`ValueAnimator` 平滑过渡
- **卡片页**：运动 / 心率 / 音乐 / 天气四张快捷卡，按 HeyTap action 启动自己的复刻应用
- **应用列表三种形态**：列表（`WearableLinearLayoutManager` 弧形）/ 宫格（3 列）/ 星图（鱼眼六边形）
- **「应用视图」切换弹窗**：官方文案 + 尺寸 + 图标 + 动效（scale 0.9↔1.0，200/250ms，`pathInterpolator(0.2,0.2,0,1)`）
- **设置页**：按官方 HeyTap 条目样式重做（52dp 高、26dp 圆角 `#ff202124`）
- **健康类页面 1:1 复刻**：心率、心率记录/统计、运动（准备→记录→结束）、睡眠、血氧、压力、运动记录
- **工具页**：天气、手电筒、指南针、电池/省电、轻智能、呼吸、时钟、日历、表盘选择
- **省电**：桌面不可见时停掉全部传感器与定时刷新（实测息屏 / 被遮挡 / 切页均归零）；「自动休眠」到点切飞行模式断网并保留蓝牙
- **工程链路**：GitHub Actions 云端自动编译 + 发布 `latest`；Windows 计划任务每 10 分钟自动同步；手机 Termux 一键安装脚本；仓库已公开，手机端无需 Token

### 4.2 进行中

- **星图页的稳定「应用视图」入口**——长按会被 `RadialAppView` 吞掉（详见第 5 节），当前只能从「设置 → 应用视图」进入，正在找不需要改星图交互的入口方案

### 4.3 待办

- 星图页固定「编辑 / 应用视图」入口
- 官方「编辑应用」功能（隐藏应用 / 应用排序）
- 环形时间自绘（`RoundTimeText`：环形刻度上的时间文字排版）
- **物理按键映射**（当前只有 Home/皇冠键被处理，见第 5 节）
- 电脑侧自动部署计划任务（开机检测手表在线就自动装最新 APK）
- 给 git 配代理（GitHub 国内时通时断）
- release 构建签名（目前只有 debug 包）

---

## 5. 当前待解决的痛点

### 5.1 星图页长按被 `RadialAppView` 吞掉

**现象**：在星图（行星视图）页长按空白处，期望弹出「应用视图」切换弹窗，实际什么都不发生。

**原因（已定位）**：

1. `MainActivity` 把 `setOnLongClickListener` 挂在 `mListScreen`（星图和列表的父容器）上。
2. 触摸事件的分发规则是：**谁消费了 `ACTION_DOWN`，长按就由谁裁决**。
3. 星图页里，若手指落在**图标之间**，子 View 不消费 DOWN，事件落到 `RadialAppView.onTouchEvent()` ——
   而它在 `ACTION_DOWN` 分支**直接 `return true`**（为了支持拖动平移，必须先拿到手势所有权）。
4. 于是父容器的 `OnLongClickListener` 永远不会触发。
5. 若手指落在**图标上**，图标子 View 消费 DOWN，长按会走图标自己的长按逻辑（弹出应用操作菜单）——这是预期行为。

**所以不是"长按没传上来"，而是 `RadialAppView` 主动抢走了 DOWN。**

**可行的解法方向**（供接手的 AI 选择，注意不要破坏"拖动平移星图"）：

- 方案 A：在 `RadialAppView` 内部自己检测长按（DOWN 后计时，未超过 `mTouchSlop` 则判定为长按），回调一个对外监听（如 `setOnBlankLongPressListener(Runnable)`），由 `MainActivity` 去打开弹窗。
- 方案 B：不在星图页做长按，改为在星图页固定放一个不占屏幕空间的「编辑」入口（例如与外圈刻度带重合的极窄按钮，或双击）。
- 方案 C：把入口做进控制中心，或保留现在的「设置 → 应用视图」。

**约束**：星图布局本身是用户明确要求保留的（不要改成翻页列表）。

### 5.2 物理按键映射

**当前状态**（这是事实，不要臆测其他键已有实现）：

- **Home / 皇冠键**：已处理。系统按下时走 `onPause → onNewIntent → onResume`，`MainActivity.onNewIntent()` 用「距上次 onPause 的间隔是否小于 500ms」判断这次 Home 是不是在本桌面上按的：
  - 在表盘首页按 → 进应用列表
  - 在应用列表按 → 回表盘首页
  - 在负一屏 / 卡片页按 → 先滚回表盘，再进列表
  - 控制中心开着时按 → 先收起，再回表盘
- **表冠旋转**：已被使用——`RadialAppView.onGenericMotionEvent()` 接收 `ACTION_SCROLL` / `AXIS_SCROLL`，在星图页做纵向平移。
- **返回键**：被主动屏蔽（`onBackPressed` 只用于收起控制中心，不允许回退页面）。

**待定义（需要项目所有者拍板，不要自行决定）**：除上述之外，还希望把哪些物理按键 / 手势映射成什么动作。
一个尚未决定的关键点：是否要引入"长按星图空白 = 打开应用视图"这类**触屏**行为（因为长按入口有冲突，见 5.1），
还是优先用**物理按键**替代触屏长按。接手前请先向项目所有者确认，不要自行假设映射表。

---

## 6. 代码写作与 UI 规则

### 6.1 交流与产出

- **始终用中文回复**，代码注释也用中文。
- 改动完成后必须说明**验证方式**（怎么编译、怎么装、怎么确认生效）。

### 6.2 代码写作

- Java 17，原生 `android.app.Activity`，**不用 AppCompat / Compose**。
- 优先**纯 XML 布局 + 官方可复用 drawable**；**只有官方本身就是 Canvas 绘制的地方**才允许自绘（例如心率折线图 `HsHrChartView`、表盘 `WatchFaceView`）。
- **禁止使用 API 29 及以上的方法 / 类**。
- 读传感器一律判空（本机没有气压计）；传感器用 `SENSOR_DELAY_NORMAL`；注册/注销严格成对。
- 数值变化一律用 `ValueAnimator` 平滑过渡，**不允许瞬间跳变**。
- 页面切换、翻页动效要**跟手**：动画时长按滑动距离自适应。
- 按 action 启动的 Intent **必须** `setPackage(getPackageName())`。
- 不要为"假想的未来需求"加抽象；三行相似代码好过一个提前抽象。

### 6.3 复刻方法（怎么从官方 APK 取数）

1. apktool 解包官方 APK → 读 `res/layout/*.xml` 层级、`res/values/dimens.xml` 尺寸、`res/values/colors.xml` 配色、`res/values-zh-rCN/strings.xml` 文案 → 1:1 照抄。
2. **px → dp 换算**：官方按 466px / 228.4dp 设计，本机 400px / 228.6dp，dp 宽度基本一致，故 **px ÷ 2.04 ≈ dp**。
3. 素材**必须**取自对应的官方 APK，**禁止跨应用混用素材**。
4. 已复刻过的尺寸参考：`dialog_applist_edit_*` 系列（标题 34px / 96px 高 / 28px 上边距，条目 120px 高、左右 24px padding）、`hey_card_default_bg_radius=30dp`、`hey_listitem_margin_horizontal=12dp`、`wear_button_corner_radius=26dp`。
5. 已复刻过的配色：`heart_rate_chart_color=#ffe64a39`、`hr_tip_text_color=#ffb0b5bf`、`hr_text_color_gray=#ff909399`、`default_background_color=#ff202124`。

### 6.4 UI 优化原则（**不占用屏幕空间**）

- 屏幕只有 400×400 且是圆屏，**任何新增 UI 都必须先确认不会挤压既有内容**。
- 优先复用官方圆形布局的**边缘留白区**（刻度带、暗角区域）放次要入口，而不是往中心区叠加控件。
- 圆屏边缘的控件不能超出安全圆，否则会被裁掉。
- 交互约定的现状**不要"优化"掉**：星图保持星图（不改翻页列表）、保留 Home 键、屏蔽返回上一页、上滑触发通知流、**只能从表盘首页进入应用列表**、表盘首页长按 = 换表盘。

### 6.5 构建与部署

- 编译：`D:\111\gradle-8.14.1\bin\gradle.bat assembleDebug --gradle-user-home D:\111\gradlehome`（cwd `D:\111\ticlauncher`）
- 安装：`D:\AndroidSDK\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`
- 云端：推送 `main` 即自动编译并发布 `latest`
- `tools/*.ps1` 保存时必须带 **UTF-8 BOM**，否则 Windows PowerShell 5.1 会静默不执行
- `tools/*.sh` 必须保持 **LF** 换行（`.gitattributes` 已强制）
