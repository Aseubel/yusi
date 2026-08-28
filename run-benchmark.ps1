# run-benchmark.ps1 —— 加载根目录 .env 后执行真实依赖基准（三层 + 统一记分卡 + 自动清理）
#
# 用法：
#   .\run-benchmark.ps1                          # local 口径（默认）
#   .\run-benchmark.ps1 -BenchmarkEnv server     # server 口径（在部署机上跑）
#   其余参数原样透传给 mvn，例如：.\run-benchmark.ps1 "-Dyusi.benchmark.gate=true"
#
# 产出：target\benchmark\benchmark-scorecard-{env}-*.json / .md

param(
    [string]$BenchmarkEnv = "local",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }

# ---------- 预检 .env ----------
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "[benchmark] 缺少 $envFile，请按模板填写后重试" -ForegroundColor Red
    exit 2
}

# 解析 KEY=VALUE；忽略注释与空行；空值跳过注入（走配置兜底）；去成对引号
$loaded = New-Object System.Collections.Generic.List[string]
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
    if ($value -ne "") {
        Set-Item -Path "Env:$key" -Value $value
        $loaded.Add($key)
    }
}
Write-Host "[benchmark] 已从 .env 注入 $($loaded.Count) 个环境变量"

# ---------- 必填项预检：缺了就让用户先填，避免 Spring 起到一半才失败 ----------
$missing = @()
foreach ($required in @("CHAT_MODEL_APIKEY", "EMBEDDING_MODEL_BASEURL", "EMBEDDING_MODEL_APIKEY", "EMBEDDING_MODEL_NAME")) {
    if (-not (Get-ChildItem "Env:$required" -ErrorAction SilentlyContinue)) { $missing += $required }
}
if ($missing.Count -gt 0) {
    Write-Host "[benchmark] 以下必填变量为空，请先补全 .env：" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    exit 3
}

# ---------- 端口连通性预检 ----------
# 只对本地地址做 TCP 探测；远程（云实例）无法用本地端口探测，跳过并提示
function Test-LocalDep {
    param([string]$Name, [int]$Port, [string]$Host_)
    if ($Host_ -notin @("127.0.0.1", "localhost")) {
        Write-Host "[benchmark] $Name 指向远程 $Host_`:$Port，跳过本地预检"
        return
    }
    $ok = Test-NetConnection -ComputerName $Host_ -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $ok) {
        Write-Host "[benchmark] 本地 $Name ($Host_`:$Port) 未就绪，请先启动依赖" -ForegroundColor Red
        exit 4
    }
}

# 从已注入的环境变量解析各依赖主机（留空走默认 127.0.0.1）
$milvusHostFromUri = $null
if ($env:MILVUS_URI -match '^https?://([^:/]+)') { $milvusHostFromUri = $Matches[1] }
Test-LocalDep -Name "MySQL" -Port 3306 -Host_ "127.0.0.1"
Test-LocalDep -Name "Milvus" -Port 19530 -Host_ $(if ($milvusHostFromUri) { $milvusHostFromUri } else { [string]$env:MILVUS_HOST })
Test-LocalDep -Name "Redis" -Port 6379 -Host_ $(if ($env:YUSI_BENCHMARK_REDIS_HOST) { [string]$env:YUSI_BENCHMARK_REDIS_HOST } else { "127.0.0.1" })

# ---------- 执行 ----------
Set-Item -Path "Env:YUSI_BENCHMARK_ENV" -Value $BenchmarkEnv
Push-Location $root
try {
    & mvn verify -Pbenchmark "-Dyusi.benchmark.env=$BenchmarkEnv" @MavenArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
