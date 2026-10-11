#!/data/data/com.termux/files/usr/bin/sh
# 手机端（Termux）一键部署：下载云端构建好的最新 APK → 安装到 TicWatch
#
# 用法（在 Termux 里）：
#   sh phone-install.sh
#   sh phone-install.sh 192.168.8.237:5555     # 指定手表地址
#
# 仓库若是私有的，先执行一次（把 <你的token> 换掉）：
#   export GH_TOKEN=<你的token>
# 仓库若是公开的，不需要 GH_TOKEN，直接跑即可。

REPO="15816563131/ticlauncher"
ASSET="app-debug.apk"
WATCH="${1:-192.168.8.237:5555}"
OUT="$HOME/$ASSET"

api_get() {
    if [ -n "$GH_TOKEN" ]; then
        curl -fsSL -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" "$1"
    else
        curl -fsSL -H "Accept: application/vnd.github+json" "$1"
    fi
}

apk_get() {
    if [ -n "$GH_TOKEN" ]; then
        curl -fL -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/octet-stream" "$1" -o "$2"
    else
        curl -fL -H "Accept: application/octet-stream" "$1" -o "$2"
    fi
}

echo "==> 安装依赖（android-tools / curl / jq）"
pkg install -y android-tools curl jq || exit 1

echo "==> 查询最新构建产物"
URL=$(api_get "https://api.github.com/repos/$REPO/releases/tags/latest" \
      | jq -r --arg n "$ASSET" '.assets[] | select(.name == $n) | .url')

if [ -z "$URL" ] || [ "$URL" = "null" ]; then
    echo "!! 没找到 $ASSET。"
    if [ -z "$GH_TOKEN" ]; then
        echo "!! 仓库是私有的，需要先设置 GH_TOKEN 再重跑。"
    else
        echo "!! 请确认云端构建成功过（GitHub Actions 页面）。"
    fi
    exit 1
fi

echo "==> 下载 $ASSET"
apk_get "$URL" "$OUT" || exit 1
ls -lh "$OUT"

echo "==> 连接手表 $WATCH"
adb connect "$WATCH"
adb -s "$WATCH" wait-for-device

echo "==> 安装到手表"
adb -s "$WATCH" install -r "$OUT"

echo "==> 完成"
