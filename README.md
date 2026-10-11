# TicLauncher — ColorOS 手表启动器（TicWatch Pro 复刻版）

在 **TicWatch Pro（初代）** 上像素级复刻 **OPPO Watch（ColorOS）** 的手表端体验。
纯原生 Android 实现（不用 AppCompat / Compose），素材全部取自解包后的 OPPO Watch X 官方 ROM。

- 仓库：https://github.com/15816563131/ticlauncher
- 分支：`main`

## 目标设备

| 项 | 值 |
|---|---|
| 设备 | TicWatch Pro（初代，Mobvoi） |
| 系统 | Wear OS 2.3 / Android 9（**API 28**） |
| 屏幕 | 400 x 400 圆屏，density 280 |
| 连接 | 无线 adb：`adb connect <watch-ip>:5555`（手表休眠后会掉线，需重连） |

## 技术栈

| 项 | 值 |
|---|---|
| 语言 | Java 17 |
| UI | 纯 XML 布局 + 原生 `android.app.Activity`（**无 AppCompat**） |
| 依赖 | `androidx.core` / `androidx.recyclerview` / `androidx.wear` / `androidx.constraintlayout` |
| 构建 | AGP 8.7.3 · Gradle 8.14.1 · JDK 17 |
| SDK | compileSdk 34 · minSdk 28 · targetSdk 30 |
| 素材 | `D:\111\oppoicons`（drawable）、`D:\111\oppowatch`（apktool 2.9.3 解包产物） |

## 云端编译（GitHub Actions，无需本地环境）

推送到 `main` 自动触发，约 1 分钟出包：

- 工作流：[.github/workflows/build.yml](.github/workflows/build.yml)
- 产物 1：Actions 页面的 Artifact `ticlauncher-debug`
- 产物 2：Release `latest` 附带的 `app-debug.apk`
- 固定下载地址：`https://github.com/15816563131/ticlauncher/releases/download/latest/app-debug.apk`

## 手机端安装（Termux，不需要电脑）

1. 安装 Termux（**必须用 F-Droid 版**，Google Play 版已废弃）：https://f-droid.org/packages/com.termux/
2. 打开 Termux，粘贴执行：

```sh
pkg install -y android-tools curl jq
curl -fsSL https://github.com/15816563131/ticlauncher/releases/download/latest/app-debug.apk -o $HOME/app.apk
adb connect 192.168.8.237:5555
adb -s 192.168.8.237:5555 install -r $HOME/app.apk
```

或者直接用仓库脚本（自动查最新构建并安装）：

```sh
curl -fsSL https://raw.githubusercontent.com/15816563131/ticlauncher/main/tools/phone-install.sh -o phone-install.sh
sh phone-install.sh
```

脚本内容见 [tools/phone-install.sh](tools/phone-install.sh)。手表 IP 变了就把地址换掉。

## 电脑端编译与安装

```powershell
# 编译（cwd = D:\111\ticlauncher）
& "D:\111\gradle-8.14.1\bin\gradle.bat" assembleDebug --gradle-user-home "D:\111\gradlehome"

# 安装
& "D:\AndroidSDK\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

自动同步：Windows 计划任务 `TicLauncher AutoSync` 每 10 分钟执行 [tools/autosync.ps1](tools/autosync.ps1)
（拉取远端 → 提交本地改动 → 推送），日志写在 `tools/autosync.log`。

## 相关文档

| 文件 | 内容 |
|---|---|
| [DEV_NOTES.md](DEV_NOTES.md) | 开发记忆：踩坑记录、硬件信息、代码架构 |
| [TODO.md](TODO.md) | 待办清单 |
| [.trae/rules/project-rules.md](.trae/rules/project-rules.md) | 项目硬性规则（Trae 会自动加载） |
