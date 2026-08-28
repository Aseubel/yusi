# 验证 .env 解析：输出将注入的变量名与数量（重点确认 CHAT_MODEL_BASEURL）
$envFile = Join-Path $PSScriptRoot "..\.env"
$keys = New-Object System.Collections.Generic.List[string]
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if ($value -ne "") { $keys.Add($key) }
}
Write-Host ("total: " + $keys.Count)
Write-Host ("CHAT_MODEL_BASEURL injected: " + ($keys -contains "CHAT_MODEL_BASEURL"))
Write-Host ("keys: " + ($keys -join ", "))
