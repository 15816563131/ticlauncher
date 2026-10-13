# TicWatch Launcher 交接文档

> 给任何接手这个项目的人（包括 AI）。内容全部来自实际代码和设备实测，**不要凭猜测修改**。

---

## 一、基础信息

### 1.1 环境与硬件

| 项 | 值 |
|---|---|
| 设备 | TicWatch Pro 初代，Wear OS 2.3 / Android 9（**API 28**） |
| 屏幕 | 400 x 400 圆屏，density 280（≈ 228.6dp） |
| adb | `D:\AndroidSDK\platform-tools\adb.exe` |
| 连接 | 无线 `adb connect <watch-ip>:5555` |
| Gradle | `D:\111\gradle-8.14.1\bin\gradle.bat --gradle-user-home D:\111\gradlehome` |
| SDK | `D:\AndroidSDK`（build-tools 34.0.0） |
| 签名 | `C:\Users\d\.android\debug.keystore`（标准 debug keystore） |
| APK 输出 | `app\build\outputs\apk\debug\app-debug.apk` |

### 1.2 传感器清单（`adb shell dumpsys sensorservice` 实测）

- **有**：
  - `TYPE_HEART_RATE`（PixArt PAH8011 "Heart Rate PPG"）
  - `TYPE_STEP_COUNTER`、`TYPE_STEP_DETECTOR`
  - `low_latency_offbody_detect`（**离腕检测传感器，已存在但未使用**）
  - Mobvoi 私有 `mobvoi_activity_recognizer` / `mobvoi_activity_data`
  - Google `com.google.wear.sensor.ppg`（原始 PPG）
  - 重力 / 线性加速度 / 磁力计 / 陀螺仪
- **没有**：`TYPE_PRESSURE`（**无气压计 → 海拔页已整体移除**）

### 1.3 关键硬约束

1. 启动器必须完全伪装，**不能有任何 TicWatch 痕迹**
2. 健康功能必须 1:1 复刻 ColorOS 手表原生应用，**不允许偏差**
3. 健康功能实现**不得依赖** Wear OS 原生健康服务
4. 不允许自定义绘制（Canvas），官方本身用 Canvas 绘制的除外（心率折线图 HsHrChartView 属于官方自绘允许）
5. 星图布局保留 4 个中心图标，不改分页列表
6. 保留 Home 键，屏蔽返回键回退功能
7. 上滑触发通知面板，不是状态栏
8. 只能从表盘主页进入应用列表，其他页面不允许

---

## 二、已完成功能

### 2.1 启动器核心
- [x] 横向三页首页：**负一屏（健康）→ 表盘主页 → 卡片页**，跟手翻页（自适应动画时长 + VelocityTracker 速度翻页）
- [x] Home 键：表盘主页按 Home 进应用列表；应用列表按 Home 回表盘
- [x] 长按表盘换表盘
- [x] 控制中心：顶部下拉，跟手拖拽 + 渐进毛玻璃背景
- [x] 应用列表三种形态：**列表 / 宫格 / 星图（行星视图）**，长按空白切换视图
- [x] 应用长按菜单（AppMenuActivity）
- [x] 数值变化平滑过渡（ValueAnimator），无瞬间切换

### 2.2 健康功能（1:1 复刻 ColorOS 原生）
- [x] **心率测量**（HeartRateActivity）：点击/进入自动测量，心形缩放动画，数值平滑过渡
- [x] **心率离腕检测 + 自动测量间隔**（2026-10-13）：注册 `low_latency_offbody_detect`，离腕停 PPG 显「未佩戴」；设置可选 连续/1/5/10 分钟/关闭，非连续用 AlarmManager 脉冲
- [x] **心率记录/统计**（HrRecordActivity）：左滑心率页进入
- [x] **运动主流程**（SportPrepareActivity → SportRecordActivity → SportEndActivity）：3 秒倒计时 → 记录页 → 结束摘要
- [x] **睡眠**（SleepActivity）
- [x] **血氧**（OxygenActivity）

所有健康 Activity 都注册了与官方一致的 HeyTap 隐式 action：
```java
// HeartRateActivity → heytap.wearable.intent.action.health.START_HEARTRATE
// SportActivity     → heytap.wearable.intent.action.health.START_DAILY
// SleepActivity     → heytap.wearable.intent.action.health.START_SLEEP
// OxygenActivity    → heytap.wearable.intent.action.health.START_OXYGEN
```

### 2.3 工具应用（ColorOS 复刻）
天气、手电筒、指南针、电池/省电、轻智能模式、呼吸放松、压力、时钟、日历、运动记录 — 全部注册 HeyTap 隐式 action。

### 2.4 省电设计
- [x] 息屏/切页/切应用时**立刻注销所有传感器**，绝不空转
- [x] 亮灭屏广播动态监听（因为 HOME 桌面息屏不触发 onPause）
- [x] 步数/心率/消耗全部走 ValueAnimator 平滑过渡
- [x] 自动休眠（设置 → 电池 → 自动休眠）：AlarmManager 精确定时，到点切飞行模式断网，结束自动恢复

### 2.5 自动化
- [x] 电脑侧自动同步计划任务 `TicLauncher AutoSync`（`tools/autosync.ps1`）
- [x] GitHub Actions 自动编译（`.github/workflows/build.yml`）
- [x] 手机端一键安装脚本（`tools/phone-install.sh`）

---

## 三、🔴 未完成 / 存在问题（含用户新请求，按优先级排序）

### 🔴 P0 - 严重 Bug

> ⚠️ **复核结论（2026-10-13）**：第 1 条「翻页方向反了」是**误判，不要 swap**。`HomePager` 三页顺序 `[负一屏(0)] [表盘(1)] [卡片(2)]`，`mDragDistance` 符号与 `page±1` 的映射与标准 pager 几何一致：**负一屏向左滑 → page+1 → 表盘（本来就对）**；负一屏向右滑算 page-1 弹回是正确行为（最左页无左侧页）。实测出现「负一屏滑不回桌面」是**旧版/被 swap 过的 APK**，用当前源码重新编译安装即可，HANDOVER 原写的「互换」会把正确代码改坏。

#### 1. 负一屏滑不回表盘（实为旧包问题）
**位置**：`HomePager.java` 第 227-247 行
**根因（原记录，已证伪）**：曾误判页码变化方向完全反了。

HomePager 的子 View 顺序：`[page0:负一屏] [page1:表盘] [page2:卡片]`
- 手指向右滑 → 内容整体向左移动 → 显示**下一个** child → page 应该 **+1**
- 手指向左滑 → 内容整体向右移动 → 显示**上一个** child → page 应该 **-1**

但当前代码（第 227-232 行）：
```java
if (mDragDistance < -w / 4f) {
    page = mPage - 1;    // ← 手指向右滑 → 写了 page 减 1
    shouldFlip = true;
} else if (mDragDistance > w / 4f) {
    page = mPage + 1;    // ← 手指向左滑 → 写了 page 加 1
    shouldFlip = true;
}
```
**方向完全反了！** 在 page0（负一屏）手指向右滑，代码算出 page = -1，然后在第 246 行判定 `page >= 0` 不满足，弹回原位置。同理 page2（卡片页）向左滑也有同样问题。

**修复方向**：把两个判断里的 `page = mPage - 1` 和 `page = mPage + 1` 互换。速度翻页部分（第 236-241 行）的 velX 方向也要同步修正。

---

### 🔴 P0 - 用户核心新需求

#### 2. 心率：离腕不检测 + 自动测量 + 可调节间隔 ✅ 已实现（2026-10-13，待真机验证）
**现状**：`HeartRateActivity` 只支持手动点击或进页面自动测量，**完全没用离腕检测**（传感器 `low_latency_offbody_detect` 已存在但代码里没注册）。

**需要实现**：
- 进入心率页后注册 `TYPE_HEART_RATE` + `low_latency_offbody_detect`
- 检测到离腕 → 停止 PPG 测量，显示「未佩戴」
- 检测到佩戴 → 按设定间隔自动启动 PPG 采样
- 设置界面：间隔时间可选（如 1 分钟 / 5 分钟 / 10 分钟）
- 间隔定时器用 `AlarmManager.setExactAndAllowWhileIdle`，不要用 postDelayed

**实现（2026-10-13）**：
- `HeartRateActivity.java` 已重写：注册 `low_latency_offbody_detect`；离腕立即 `showOffBody()` 停 PPG、心形 alpha 0.35；佩戴自动 `startBurst(true)` 续测。
- 自动间隔：`KEY_HR_INTERVAL`（分钟，0=连续/1/5/10/ -1=关闭）存 `SettingsActivity.PREFS`；非连续模式按 `AlarmManager.setExactAndAllowWhileIdle` 排脉冲（`scheduleHrTick`），突发最长 `BURST_MS=12s` 后 `endBurst(true)` 续测。
- 设置页 `activity_settings.xml` 新增「心率自动测量」条目（`btn_hr_interval` / `hr_interval_text`），`SettingsActivity.java` 点击循环 0→1→5→10→-1。
- ⚠️ **离腕传感器语义坑（已修正）**：`TYPE_LOW_LATENCY_OFFBODY_DETECT` 的 `values[0]` 是 **1.0=离腕、0.0=佩戴**（与直觉相反），所以 `onBody = values[0] < 0.5f`。最初写成 `>= 0.5` 是反的，会把「戴着」判成「离腕」。

#### 3. 健康应用进不去
**可能原因（需排查）**：
- **原因 A**：`launchEntry()` 用的是 `Intent.ACTION_MAIN + CATEGORY_LAUNCHER`，但某些健康 Activity 虽然声明了这个 intent-filter，实际启动时可能有异常
- **原因 B**：设备上的 OPPO 原生健康 APK 可能也声明了相同的 launch category，导致 `PackageManager.queryIntentActivities` 返回了 OPPO 的那个，启动后崩溃（参考 DEV_NOTES 第 1 条坑：OPPO APK 按 API 30 编译，在 API 28 上必崩）
- **原因 C**：`HeartRateActivity` 等健康 Activity 的 `exported=true` 是对的，但 `launchMode=singleTop` 可能导致返回栈问题

**排查步骤**：
1. `adb shell am start -n com.trae.ticlauncher/.HeartRateActivity` 看是否能直接启动
2. `adb shell dumpsys package | findstr HeartRateActivity` 看 intent-filter 是否正确
3. 检查 `AppListAdapter.launch()` 和 `MainActivity.launchEntry()` 是否都用了 `setPackage(getPackageName())`（launchEntry 里没加，这是隐患！）

#### 4. 应用列表图标重复
**位置**：`MainActivity.java` 第 952-968 行 `selfAppIcon()` 方法
**可能重复映射**：
- `ic_app_settings` 同时给了「设置」和「轻智能」（SmartModeActivity）
- `ic_app_alarmclock` 给了「时钟」，`ic_app_stopwatch` 给了「运动记录」（WorkoutActivity）— 如果用户觉得这俩算重复，需要换独立图标
- `PowerSaveActivity` 用了 `ic_battery_100`，可能和系统某个图标撞

**修复方向**：从 `D:\111\oppoicons` 里找对应的 ColorOS 官方图标替换，不要用现有 drawable 凑合。

#### 5. 没有应用商店
**现状**：TicWatch Pro 原生没有 Google Play，也没有 OPPO 商店，这是硬件限制。
**解决方案**：
- 可以考虑做一个「第三方应用安装页」，让用户 adb push APK 到手表后从本启动器里安装
- 或者在设置里加一个「开发者选项 → 允许安装未知来源」开关 + 文件管理器

---

### 🟡 P1 - 中等问题

#### 6. 应用内无法左滑返回
**现状**：
- `HeartRateActivity` 的左滑**被设计成跳到 HrRecordActivity**（setupSwipeToRecord 方法），不是返回
- 其他 Activity 完全没有返回手势，只能点返回按钮（有的有，有的没有）
- Wear OS 系统级的「边缘左滑返回」可能被拦截

**需要做的**：
- 给所有二级 Activity 加上「左滑 = finish()」的手势（或者改 HeartRateActivity 左滑为返回，长按或点按钮进记录页）
- 确保从健康应用返回后，HomePager 能回到正确的页面

#### 7. 负一屏心率卡片自动测量间隔 ✅ 已实现（2026-10-13，待真机验证）
**位置**：`HealthPanelView.java`
**现状**：只要负一屏可见，就一直注册 PPG 传感器，实时采样。
**需要**：可以调节刷新间隔（比如 30 秒 / 1 分钟 / 关闭），离腕时停止。

**实现（2026-10-13）**：
- `HealthPanelView.java` 消费 `KEY_HR_PANEL_INTERVAL`（秒：0=连续 / 30 / 60 / -1=关闭）。
- 注册 `low_latency_offbody_detect`；`onSensorChanged` 里处理离腕（停 PPG、显「未佩戴」、心形/文字 alpha 0.4）与重新佩戴（按间隔恢复测量）。
- 连续模式常注册 PPG；突发模式按 `HR_BURST_MS=12s` 测一阵→停一阵→`postDelayed` 续测（用 Handler，视图只在可见时存活，比 AlarmManager 轻）。
- 设置页新增「负一屏心率」条目（`btn_hr_panel` / `hr_panel_text`），点击循环 0→30→60→-1。
- 离腕语义与 P0-2 一致（`values[0] < 0.5f` = 佩戴）。

---

### 🟢 P2 - 可选优化

- [ ] 星图页长按进入「编辑应用」功能（复刻 ColorOS 隐藏应用 / 排序）
- [ ] 手表息屏时自动同步最新 APK（电脑侧计划任务扩展）
- [ ] Release 构建签名配置
- [ ] GitHub 代理配置（国内直连不稳）
- [ ] 环形时间自绘（RoundTimeText，官方本身就用 Canvas，允许）

---

## 四、代码架构速查

### 4.1 主要类职责

| 类 | 职责 |
|---|---|
| `MainActivity` | 主窗口：首页三页 + 应用列表 + 控制中心调度；Home 键分发；返回键屏蔽 |
| `HomePager` | 三页横向分页容器（**有 page 方向 bug，见 P0-1**） |
| `HealthPanelView` | 负一屏健康卡片（步数/心率/消耗），XML 布局 + ValueAnimator |
| `WatchFaceView` | 表盘主页 |
| `CardPanelView` | 卡片页（四张快捷卡 → Intent action 映射） |
| `RadialAppView` | 星图视图（行星形态应用列表） |
| `AppListAdapter` | 应用列表适配器，支持列表/宫格/星图三种模式 |
| `HeartRateActivity` | 心率测量页（**需加离腕检测 + 自动间隔**） |
| `HrRecordActivity` | 心率记录/统计页 |
| `SportActivity` + 3个子 Activity | 运动主流程（准备→记录→结束） |
| `SleepActivity` | 睡眠 |
| `OxygenActivity` | 血氧 |
| `StepSource` | 步数→卡路里计算 + 每日零点归零 |
| `SleepModeReceiver` | 自动休眠定时调度（AlarmManager 精确闹钟链） |

### 4.2 省电核心逻辑

统一由 `MainActivity.updatePageActivity()` 决定「哪一页可以干活」：
```java
boolean homeVisible = mForeground && !mShowingList && isScreenOn();
// isScreenOn() = PowerManager.isInteractive()
// 注意：HOME 桌面息屏**不触发 onPause**，必须显式查屏幕状态
```

### 4.3 Action 劫持防护

凡是 `heytap.wearable.intent.action.*` 的 Intent，**必须 `setPackage(getPackageName())`**，否则会被设备上的 OPPO 原生 APK（按 API 30 编译，在 API 28 上必崩）抢走启动权。

`MainActivity.launchCard()` 已经做了，但 `launchEntry()` 和 `AppListAdapter.launch()` **没加**，需要补上。

### 4.4 应用列表图标映射

`MainActivity.selfAppLabel()` 和 `selfAppIcon()` 负责给本应用内的复刻 Activity 分配独立的名称和图标，因为它们的 `android:label` 都继承自 application。

---

## 五、踩过的坑（必看）

1. **绝对不要直接移植 OPPO 官方 APK**：minSdk=30，R8 常量折叠后只剩 API 30 调用，补不完。只能原生重写。
2. **OPPO APK 抢 Intent**：必须 `setPackage(getPackageName())` 限定启动范围。
3. **无气压计**：海拔页已删，任何读传感器的代码必须判空。
4. **PowerShell 脚本必须 UTF-8 BOM**：否则 PowerShell 5.1 按 GBK 解码，脚本静默失效。
5. **HOME 桌面息屏不触发 onPause**：必须显式查 `PowerManager.isInteractive()` + 亮灭屏广播。
6. **无线 adb 会掉线**：超时先 disconnect 再重连。
7. **星图页吞长按**：当前唯一可靠的「应用视图」切换入口是设置页。
8. **系统 NTP 对时失败**：不要假设系统时间一定准。
9. **低电量会死机**：做长时间传感器采集前先确认电量。
10. **依赖走阿里云镜像**：settings.gradle 里配了 maven.aliyun.com。
11. **GitHub 直连不稳**：`api.github.com` 和 `codeload.github.com` 通，`github.com` 被墙。
12. **exported=false 的 Activity 无法 adb 直接启动**：调试时临时改 true，验证完改回去。
13. **关 Wi-Fi 会被系统撤销**：Wear OS 的 WearWifiMediator 强制保持网络，只有飞行模式是它认的。
14. **低电量测试前确认电量充足**：设备会无预警死机。
15. **离腕传感器语义相反**：`TYPE_LOW_LATENCY_OFFBODY_DETECT` 的 `values[0]` 是 **1.0=离腕、0.0=佩戴**（`onBody = value < 0.5f`）。写成 `>= 0.5 == 佩戴` 是反的，会让「戴着」被误判成「离腕」、离腕时反而继续测。两种心率路径（HeartRateActivity + HealthPanelView）都已按正确语义写。

---

## 六、复刻方法论

1. `apktool` 解包官方 APK → 读 `res/layout/*.xml`（层级）、`res/values/dimens.xml`（尺寸）、`res/values/colors.xml`（配色）、`res/values-zh-rCN/strings.xml`（文案），1:1 照抄
2. **px → dp 换算**：官方按 466px / 228.4dp 设计，本机 400px / 228.6dp，dp 宽度基本一致 → **px ÷ 2.04 ≈ dp**
3. 素材仓库：`D:\111\oppoicons`（图标）、`D:\111\oppowatch`（150 个 APK + 解包产物）
4. 动效复刻：`applist_edit_dialog_in/out` = scale 0.9↔1.0 + alpha，200ms/250ms，插值器 `pathInterpolator(0.2, 0.2, 0.0, 1.0)`

更完整的官方 ROM 解包成果见 `D:\111\oppowatch\OPPO_Watch_X_解包成果清单.md`。

---

## 七、下一步建议

| 优先级 | 任务 | 预计工作量 |
|---|---|---|
| P0 | 修 HomePager 翻页方向 bug | ✅ 已证实为误判，无需改（重编译即可） |
| P0 | 心率页加离腕检测 + 自动测量间隔 | ✅ 已实现（待真机验证） |
| P0 | 排查并修复健康应用进不去问题 | 1 小时 |
| P0 | 修复应用列表图标重复 | 30 分钟 |
| P1 | 所有二级 Activity 加左滑返回手势 | 1 小时 |
| P1 | 负一屏心率卡片可调节刷新间隔 | ✅ 已实现（待真机验证） |
| P2 | 应用商店 / 第三方 APK 安装页 | 按需 |
| P2 | 星图长按编辑应用 | 按需 |
