# run-backend.ps1 —— 加载根目录 .env 后本地启动后端（dev profile，默认端口 8080）
#
# 用法：
#   .\run-backend.ps1                       # 前台运行（Ctrl+C 停止）
#   .\run-backend.ps1 -Detached             # 独立后台进程，日志写 target\backend.log（关终端不受影响）
#   .\run-backend.ps1 -Stop                 # 停掉后台运行的实例
#   .\run-backend.ps1 -Port 8080
param(
    [int]$Port = 8080,
    # 默认从副本 jar 启动：避免锁住 yusi-0.0.1-SNAPSHOT.jar，导致 benchmark 的 mvn repackage rename 失败
    [string]$JarPath = "target\yusi-backend-run.jar",
    [switch]$Detached,
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
$logOut = Join-Path $root "target\backend.log"
$logErr = Join-Path $root "target\backend-err.log"

function Get-BackendPid {
    # 按 SERVER_PORT 对应的监听端口找 java 进程
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) {
        $p = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq "java") { return $p.Id }
    }
    return $null
}

if ($Stop) {
    $pidToKill = Get-BackendPid
    if ($pidToKill) {
        Stop-Process -Id $pidToKill -Force
        Write-Host "[backend] stopped pid=$pidToKill"
    } else {
        Write-Host "[backend] no running instance on port $Port"
    }
    exit 0
}

$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "[backend] 缺少 $envFile" -ForegroundColor Red
    exit 2
}

# 与 run-benchmark.ps1 相同的 .env 解析逻辑
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if ($value.Length -ge 2 -and (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    if ($value -ne "") { Set-Item -Path "Env:$key" -Value $value }
}

$jar = Join-Path $root $JarPath
if (-not (Test-Path $jar)) {
    Write-Host "[backend] 找不到 $jar，请先 mvn package" -ForegroundColor Red
    exit 2
}

$javaArgs = @("-jar", $jar,
    "--spring.profiles.active=dev",
    # show-sql 关闭：dev profile 默认 true，任务轮询 SQL（embedding_task/life_graph_task）会刷屏
    "--spring.jpa.show-sql=false",
    # 本机 9090 被 verge-mihomo（Clash Verge）占用；9091 曾与其出站源端口瞬时相撞，用冷门端口 29090
    "--grpc.server.port=29090")

if ($Detached) {
    $existing = Get-BackendPid
    if ($existing) {
        Write-Host "[backend] 已有实例运行 pid=$existing，先执行 .\run-backend.ps1 -Stop" -ForegroundColor Yellow
        exit 3
    }
    $env:SERVER_PORT = "$Port"
    # 独立进程：环境变量从当前会话继承，终端关闭不影响
    Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $root `
        -RedirectStandardOutput $logOut -RedirectStandardError $logErr -WindowStyle Hidden
    Write-Host "[backend] detached 启动中 port=$Port, 日志: $logOut"
    exit 0
}

Write-Host "[backend] 启动 $jar (dev, port=$Port)"
Set-Item -Path "Env:SERVER_PORT" -Value "$Port"
& java @javaArgs
