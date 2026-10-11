---
description: TicLauncher 项目（TicWatch Pro 上复刻 ColorOS 手表端）必须遵守的硬性规则
alwaysApply: true
---

# TicLauncher 项目规则

## 交流与产出

- **始终用中文回复**，代码注释也用中文。
- 改动完成后必须说明**验证方式**（怎么编译、怎么装、怎么确认生效）。

## 红线（绝对不要做）

- **绝不动系统底层引导文件**（`xbl` / `preloader` / bootloader / 分区表等一律不碰）。
  本项目只在应用层（APK）范围内开发。
- **绝对不要尝试移植 OPPO 官方 APK 到本机**。官方 APK 是 `minSdkVersion=30` 编译的，
  R8 会把 SDK 版本判断常量折叠掉，调用链不收敛（详见 `DEV_NOTES.md` 第二节第 1 条）。
  一律用原生代码重写。
- **不要占用 C 盘空间**：项目文件、SDK、缓存、临时产物都不要往 C 盘放。

## 素材与复刻

- 素材**必须**取自对应的官方 APK，**禁止跨应用混用素材**。
- 优先使用**纯 XML 布局 + 官方可复用 drawable**；**只有官方本身就是 Canvas 绘制的地方**
  才允许自绘。
- 像素级复刻：尺寸、配色、文案、动效严格照抄官方
  （`res/values/dimens.xml` / `colors.xml` / `values-zh-rCN/strings.xml` / `res/anim/*`）。
- 所有页面切换和数据更新必须平滑，不允许瞬间跳变；数值变化用 `ValueAnimator`。

## 兼容性

- **禁止使用 API 29 及以上的方法 / 类**（目标设备只到 API 28）。
- 读传感器一律判空（本机没有气压计 `TYPE_PRESSURE`）。
- 传感器用 `SENSOR_DELAY_NORMAL`，注册/注销严格成对写在 `onResume` / `onPause`。

## 交互约定（保持现状，不要"优化"掉）

- 星图布局保持星图，**不要改成翻页列表**。
- **保留 Home 键**，屏蔽返回上一页的功能。
- **上滑触发通知面板**（不是状态栏）。
- **只能从表盘首页进入应用列表**，其他页面不允许进入。
- 表盘首页长按 = 换表盘。
- 最终产物必须完全伪装，**不能留下 TicWatch 的痕迹**。

## 健康功能

- 健康类页面必须是 ColorOS 手表原生应用的 **1:1 复刻**，不允许自由发挥。
- **不要使用 Wear OS 原生的健康服务**，一律读 TicWatch 自己的传感器（`SensorManager`）。
- 注册与官方一致的 HeyTap 隐式 action，例如
  `heytap.wearable.intent.action.health.START_HEARTRATE`。
- 所有按 action 启动的 Intent **必须** `setPackage(getPackageName())`，
  否则会被同 action 的 OPPO APK 劫持。

## 构建与部署

- 编译：`D:\111\gradle-8.14.1\bin\gradle.bat assembleDebug --gradle-user-home D:\111\gradlehome`
- 安装：`D:\AndroidSDK\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`
- 云端流水线：推送 `main` 即自动编译并发布 `latest`（见 `.github/workflows/build.yml`）。
- `tools/*.ps1` 保存时必须带 **UTF-8 BOM**，否则 Windows PowerShell 会静默不执行。
