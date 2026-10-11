# 开发记忆（DEV_NOTES）

给后续接手的人（包括 AI）看的项目记忆。**内容都是实际踩过的坑和已核实的事实**，不要凭猜测改动。

---

## 一、环境与硬件事实

| 项 | 值 |
|---|---|
| 设备 | TicWatch Pro 初代，Wear OS 2.3 / Android 9（**API 28**） |
| 屏幕 | 400 x 400 圆屏，density 280（≈ 228.6dp） |
| adb | `D:\AndroidSDK\platform-tools\adb.exe` |
| 设备连接 | 无线 `adb connect <watch-ip>:5555` |
| Gradle | `D:\111\gradle-8.14.1\bin\gradle.bat`，`--gradle-user-home D:\111\gradlehome` |
| Android SDK | `D:\AndroidSDK`（build-tools 34.0.0） |
| apktool | `D:\111\tools\apktool.jar`（2.9.3） |
| 官方素材 | `D:\111\oppoicons`、`D:\111\oppowatch`（150 个 APK + 解包产物） |
| 签名 | `C:\Users\d\.android\debug.keystore`（标准 debug keystore） |

### 传感器清单（`adb shell dumpsys sensorservice` 实测）

- **有**：`TYPE_HEART_RATE`（**PixArt PAH8011** "Heart Rate PPG"）、`TYPE_STEP_COUNTER`、
  `TYPE_STEP_DETECTOR`、`TYPE_ROTATION_VECTOR` / 重力 / 线性加速度 / 磁力计 / 陀螺仪、
  `low_latency_offbody_detect`、Mobvoi 私有 `mobvoi_activity_recognizer` / `mobvoi_activity_data`、
  Google `com.google.wear.sensor.ppg`（原始 PPG）
- **没有**：`TYPE_PRESSURE`（**无气压计**）

---

## 二、踩过的坑

### 1. 绝对不要移植 OPPO 官方 APK 到 API 28（最大的一条）

OPPO Watch 的健康类 APK 都是 **`minSdkVersion=30`** 编译的。R8 会把
`Build.VERSION.SDK_INT >= 29/30` 这类守卫**常量折叠成 `true`**，把兼容分支整段删掉，
于是二进制里只剩 API 30 才有的调用。实际遇到的错误链（按顺序）：

1. `NoClassDefFoundError: android.os.ext.SdkExtensions` — 可以写 shim 补
2. `NoSuchMethodError: Activity.registerActivityLifecycleCallbacks` — 可以 smali 补
3. `NoSuchMethodError: View.saveAttributeDataForStyleable` — **补不了**：
   `ViewCompat` 把这个调用内联进了每一个 AndroidX 控件的构造函数里

到第 3 条就不收敛了（除非把整个 AndroidX 栈重编）。**结论：一律用原生重写。**

同时这也带来一条衍生规则：OPPO 的 APK 如果恰好装在手表上，它会声明**同样的 HeyTap action**。
所以本项目所有按 action 启动的 Intent 都必须 `intent.setPackage(getPackageName())`，
否则会被 OPPO 的 APK 抢走启动并崩溃。

### 2. 同 action 被劫持

见上。凡是 `heytap.wearable.intent.action.*` 的调用都要 `setPackage`。

### 3. 没有气压计 → 海拔页已删除

`getDefaultSensor(TYPE_PRESSURE)` 返回 null，海拔页没有任何数据可显示，
已整体移除（`AltitudeActivity` 从 manifest 和代码中删掉）。**任何读传感器的代码都必须判空。**

### 4. PowerShell 脚本必须存成 UTF-8 with BOM

`tools/autosync.ps1` 一度怎么写日志都不生成、也不报错。原因是文件存成了
**UTF-8 无 BOM**，Windows PowerShell 5.1 会按 ANSI（GBK）解码，中文注释把脚本解析搞坏，
而 `$ErrorActionPreference` 又把错误吞了。**加 BOM 后立刻正常。**
凡是 `tools/*.ps1`，编辑后都要确认前三字节是 `EF BB BF`。

（另外：不要在计划任务/`-File` 上下文里用 `$env:TEMP`，实测会被解析成异常路径，
日志请用脚本同目录或绝对路径。）

### 5. `<pathInterpolator>` 不能当 `<set>` 的子元素

复刻官方弹窗动效时想内联写会直接编译/运行报错。必须做成独立资源
（`res/anim/task_anim_interpolator.xml`），再用 `android:interpolator="@anim/..."` 引用。

### 6. `exported=false` 的 Activity 无法用 adb 直接启动

调试时 `adb shell am start` 会报 `not exported from uid`。
临时把 `android:exported` 改 `true` 验证，**验证完必须改回 `false`**。

### 7. 手表息屏时 adb 截图全黑 / `mCurrentFocus` 为空

先唤醒再调试：

```sh
adb shell input keyevent KEYCODE_WAKEUP
adb shell svc power stayon true
```

### 8. 无线 adb 会不定时掉线

超时后先 `adb disconnect` 再重连；手表休眠、重启后都要重新 `adb connect`。

### 9. 星图页（RadialAppView）会吞掉长按

在星图页做长按进入「应用视图」实测无效（事件被 RadialAppView 消费）。
**当前可靠入口是「设置 → 应用视图」**，固定入口待补（见 TODO）。

### 10. 系统 NTP 对时失败

目标设备即使 Wi-Fi 正常，系统自动对时也失败，需要手动校时。
时间相关的显示逻辑不要假设系统时间一定准。

### 11. 低电量下会直接死机

设备在低电量时会无预警死机/失去响应。做长时间传感器采集、长时间 adb 调试前，
**先确认电量充足**。

### 12. 依赖下载走阿里云镜像

`settings.gradle` 里配了 `maven.aliyun.com` 镜像优先，否则国内环境拉依赖极慢。

### 13. GitHub 直连不稳

国内访问 github.com / raw.githubusercontent.com 时通时断。
`tools/autosync.ps1` 每 10 分钟跑一次，失败会自己记日志并在下一轮重试，不需要人工干预。

**这台机器上的实测结果**（`Test-NetConnection`）：

```
github.com          :443  -> False   ← git push/pull 走的域名，被墙
api.github.com      :443  -> True    ← 可用
codeload.github.com :443  -> True    ← 可用
```

也就是说 `git push` 失败时，可以用 **GitHub 的 Git Data API** 走 `api.github.com` 把提交推上去
（blobs → tree → commit → 更新 ref）。本地那个提交和 API 生成的提交内容相同但 SHA 不同，
下一次成功的 `git pull --rebase` 会把这个空提交自动丢掉。

### 14. 息屏时桌面**不会**收到 onPause

这是本项目最容易踩的一个坑，直接影响耗电：

```
adb shell input keyevent KEYCODE_SLEEP
adb shell dumpsys activity activities | grep ResumedActivity
  → 仍是 com.trae.ticlauncher/.MainActivity
```

也就是说手表灭屏后，HOME 桌面依然处于 **resumed** 状态，`onPause` 根本不会来。
所以**绝对不能只靠 `onResume` / `onPause` 判断「桌面是否可见」**，
否则息屏后传感器（尤其是 PPG 心率）会整夜跑着。

正确做法（已实现于 `MainActivity.updatePageActivity()`）：

- 判断条件用 `mForeground && !mShowingList && isScreenOn()`，
  其中 `isScreenOn()` = `PowerManager.isInteractive()`；
- 另外动态注册 `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` 广播，
  亮灭屏时立刻重新计算一次。

（注意：`input keyevent 26` 是**切换**开关，做息屏/亮屏测试要用
`KEYCODE_SLEEP` / `KEYCODE_WAKEUP`，否则会把测试结论搞反。）

### 15. 应用层关不掉 Wi-Fi；飞行模式广播不能发

**（1）关 Wi-Fi 会被系统的 Wear OS 策略层撤销。**
`WifiManager.setWifiEnabled(false)` **是生效的**，日志里能看到 Wi-Fi 真的被关掉了：

```
WifiStateMachinePrime: Switching from ClientModeActiveState to WifiDisabled
wifi driver unloaded / Wifi HAL stopped
ConnectivityService: WIFI going from CONNECTED to DISCONNECTED
```

但约 **440ms** 后系统自己又把它拉回来：

```
D/WearWifiMediator: Wifi is OFF but mWifiDesired is TRUE. Updating state...
D/WearWifiMediator: setWifiDecision: true; reason: ON_NETWORK_REQUEST
D/WifiService: setWifiEnabled: true (package=android uid=1000)
```

`WearWifiMediator` 认为「手表必须有网络」，任何应用级的关闭都会被它撤销；
把 `Settings.Global.WIFI_ON` 一起写也没用。
**结论：只有飞行模式是它能认的机制。**

**（2）`ACTION_AIRPLANE_MODE_CHANGED` 是受保护广播，普通应用发不了。**

```
SecurityException: Permission Denial: not allowed to send broadcast
  android.intent.action.AIRPLANE_MODE from pid=…, uid=10060
```

只要写 `Settings.Global.AIRPLANE_MODE_ON` 就够了（框架用 ContentObserver 监听这个值，
写进去几百毫秒后就会真正执行，实测生效）。
控制中心的飞行模式开关原来在写完之后补发这个广播，**而且没有 try/catch，点一下直接崩**，已修。

### 16. 用 adb 测「关 Wi-Fi」一定会把自己锁在门外

手表是靠 Wi-Fi 连 adb 的，一旦真的关掉 Wi-Fi，adb 就断了，没法再把它开回来。
测这类东西必须让**被测代码自己负责恢复**（例如把休眠窗口的结束时间设在 1~3 分钟后），
否则只能人工去手表上把网络打开。

---

## 三、代码架构

### 入口与主窗口

`MainActivity`（`android.intent.category.HOME`，默认桌面）承载全部主界面。
[activity_main.xml](app/src/main/res/layout/activity_main.xml) 的层序（自下而上）：

```
home_screen   表盘首页（亮屏即见）
list_screen   应用列表（只在表盘页按 Home/皇冠键进入）
quick_blur    下拉控制中心的毛玻璃层（随下拉比例淡入）
quick_panel   控制中心面板（跟手下滑）
```

### 表盘首页 = 横向三页

`HomePager`（自定义 ViewGroup）装着三页，默认停在中间：

```
[负一屏 HealthPanelView]  [表盘主页 WatchFaceView]  [卡片页 CardPanelView]
```

翻页规则：拖动超过 1/4 屏宽 **或** 松手横向速度 > 1000 px/s 即翻页；
settle 动画时长按距离自适应（140~320ms）；首尾到边界弹回，**不做循环回卷**。

### 主要类职责

| 类 | 职责 |
|---|---|
| `MainActivity` | 主窗口：首页 / 应用列表 / 控制中心的调度，Home 键与手势分发 |
| `HomePager` | 首页横向分页容器（跟手 + 速度翻页） |
| `HealthPanelView` | 负一屏（健康数据卡片，XML 布局 + ValueAnimator 数值过渡） |
| `WatchFaceView` | 表盘主页 |
| `CardPanelView` | 卡片页 |
| `RadialAppView` | 星图视图（行星视图） |
| `AppListAdapter` / `AppEntry` | 应用列表适配器；支持列表 / 宫格 / 星图三种模式（`MODE_LIST`/`MODE_GRID`/`MODE_STAR`） |
| `AppViewStyleActivity` | 「应用视图」切换弹窗（复刻官方 `dialog_applist_edit`） |
| `AppMenuActivity` | 应用长按操作菜单 |
| `SettingsActivity` | 设置页（按官方 HeyTap 条目样式） |
| `WatchFaceActivity` | 表盘选择 |
| `QuickPanelController` | 控制中心逻辑 |
| `HomeHelper` / `StepSource` / `CircleIconDrawable` | 首页辅助 / 步数数据源 / 圆形图标 drawable |
| `HsHrChartView` | 心率折线图（**Canvas 自绘**，官方本身就是绘制） |

### 健康与工具类 Activity

- 健康：`HeartRateActivity`、`HrRecordActivity`（心率记录/统计页）、`SportActivity`、
  `SportPrepareActivity` → `SportRecordActivity` → `SportEndActivity`、`SleepActivity`、`OxygenActivity`
- 工具：`WeatherActivity`、`FlashlightActivity`、`CompassActivity`、`PowerSaveActivity`、
  `SmartModeActivity`、`RelaxActivity`、`StressActivity`、`ClockActivity`、`CalendarActivity`、
  `WorkoutActivity`

每个健康/工具 Activity 都注册了与官方一致的 HeyTap 隐式 action，例如
`heytap.wearable.intent.action.health.START_HEARTRATE`、`...START_SLEEP`、
`heytap.wearable.intent.action.START_WEATHER`。

### 模块化约定

- **纯 XML 布局 + 原生 API**；官方本身是 Canvas 绘制的地方才允许自绘
- 数值变化一律用 `ValueAnimator` 平滑过渡，不要瞬间切换
- 传感器注册/注销严格成对写在 `onResume` / `onPause`，用 `SENSOR_DELAY_NORMAL`
- 只在真正测量时加 `FLAG_KEEP_SCREEN_ON`

### 省电设计（核心原则：不看得见就不干活）

统一由 `MainActivity.updatePageActivity()` 决定「哪一页可以干活」，条件为
**桌面在前台 + 没被别的应用盖住 + 屏幕亮着 + 该页正显示**。不满足时：

| 组件 | 不可见时 | 会不会丢数据 |
|---|---|---|
| `WatchFaceView` | 注销 `TYPE_STEP_COUNTER` + `ACTION_TIME_TICK` 广播 | 不会。步数是硬件累计计数器，恢复时立刻读到当前值；时间在 `onDraw` 里现取 |
| `HealthPanelView` | 注销 `TYPE_STEP_COUNTER` + `TYPE_HEART_RATE`(PPG) | 不会。心率卡片是实时概览，重新可见时马上重新采样 |

实测验证（`dumpsys sensorservice` 里看 ticlauncher 持有的活跃传感器）：

| 场景 | 结果 |
|---|---|
| 表盘页 · 亮屏 | `Step Counter` 活跃（预期） |
| 负一屏 · 亮屏 | `Heart Rate PPG` + `Step Counter` 活跃（预期） |
| 应用列表 / 设置在前台 | 无 |
| 息屏 7 秒后 | **无** |
| 别的应用在前台 | **无** |
| 重新亮屏 / 返回桌面 | 自动恢复，无需重启 |

另外：`HealthPanelView` 的步数回调做了「值没变就不重开 ValueAnimator」的判断，
避免每走一步都新建两个动画。

### 自动休眠（设置 → 电池 → 自动休眠）

之前这个开关是**空壳**（只写偏好，全项目没人读）。现在由 `SleepModeReceiver` 负责：

- 用 `AlarmManager.setExactAndAllowWhileIdle` 在「开始 / 结束」两个时刻各定一个精确闹钟，
  平时不驻留进程、不轮询、不占唤醒锁；每次触发后自己续排下一个边界，形成闭环。
- **开始**：切飞行模式断网（Wi-Fi / 蜂窝）→ 再把蓝牙补开回来，保住与手机的通知链路。
- **结束**：关飞行模式，网络由系统自动恢复。
- 触发点：桌面 `MainActivity.onCreate`（自愈，进程被杀/重启后重新排上）、
  `PowerSaveActivity` 的开关与时间改动、以及闹钟自身。

实测（`adb logcat -s SleepMode` + 端口轮询）：

```
20:15:00.034  sleep start: airplane on (network cut)
20:15:01.237  bt -> true          ← 飞行模式连带关掉的蓝牙被补回来
20:17:01.063  sleep end: airplane off (network restored)
```

| 时刻 | 手表 Wi-Fi 可达 |
|---|---|
| 20:14:54 | True |
| 20:15:23 | **False**（休眠窗口内，网络已切断） |
| 20:17:15 | True（窗口结束，自动恢复） |

注意：休眠窗口内靠 Wi-Fi 连 adb 会断开，这是预期行为，到点会自动恢复。

---

## 四、复刻方法论（怎么从官方 APK 拿到数据）

1. `apktool` 解包官方 APK → 读 `res/layout/*.xml` 的层级、`res/values/dimens.xml` 的尺寸、
   `res/values/colors.xml` 的配色、`res/values-zh-rCN/strings.xml` 的中文文案，然后 1:1 照抄。
2. **px → dp 换算**：官方按 466px / 228.4dp 设计，本机 400px / 228.6dp，
   dp 宽度基本一致，所以 **px ÷ 2.04 ≈ dp**。
3. 复刻过的尺寸参考：`dialog_applist_edit_*` 系列（标题 34px / 96px 高 / 28px 上边距，
   条目 120px 高、左右 24px padding）、`hey_card_default_bg_radius=30dp`、
   `hey_listitem_margin_horizontal=12dp`、`wear_button_corner_radius=26dp`。
4. 复刻过的配色：`heart_rate_chart_color=#ffe64a39`、`hr_tip_text_color=#ffb0b5bf`、
   `hr_text_color_gray=#ff909399`、`default_background_color=#ff202124`。
5. 官方动效：`applist_edit_dialog_in/out` = scale 0.9↔1.0 + alpha，200ms / 250ms，
   插值器 `pathInterpolator(0.2, 0.2, 0.0, 1.0)`。

更完整的官方 ROM 解包成果见 `D:\111\oppowatch\OPPO_Watch_X_解包成果清单.md`。
