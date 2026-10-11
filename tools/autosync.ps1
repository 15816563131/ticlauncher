# tools/autosync.ps1
# 电脑侧自动同步：拉取远端（手机上的改动）→ 提交本地改动 → 推送
# 由 Windows 计划任务「TicLauncher AutoSync」每 10 分钟调用一次。
# 日志写在与脚本同目录的 autosync.log。
# 注意：不要用 $env:TEMP —— 计划任务 / -File 上下文里它会被解析成异常路径。

$repo = Split-Path -Parent $PSScriptRoot          # tools 的上一级 = 仓库根目录
$log  = Join-Path $PSScriptRoot 'autosync.log'

function Log($m) {
    $line = "{0}  {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $m
    try { [System.IO.File]::AppendAllText($log, $line + "`r`n", [System.Text.Encoding]::UTF8) } catch { }
}

Log '========== start =========='

# 1) 先把手机推上来的改动合并进来（本地未提交改动自动暂存）
git -C $repo pull --rebase --autostash 2>&1 | ForEach-Object { Log "pull | $_" }

# 2) 有本地改动就提交
git -C $repo add -A 2>&1 | ForEach-Object { Log "add  | $_" }
$status = git -C $repo status --porcelain

if ($status) {
    $msg = 'auto: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
    git -C $repo -c user.name="ticwatch-dev" -c user.email="ticwatch-dev@localhost" `
        commit -q -m $msg 2>&1 | ForEach-Object { Log "commit | $_" }

    # 3) 推送到远端
    git -C $repo push 2>&1 | ForEach-Object { Log "push | $_" }
    Log "pushed: $msg"
} else {
    Log 'no local changes'
}
