$bytes = [System.IO.File]::ReadAllBytes('d:\develop\projects\yusi\run-backend.ps1')
Write-Host ("file length: " + $bytes.Length)
Write-Host ("first 6 bytes: " + (($bytes[0..5] | ForEach-Object { $_.ToString('X2') }) -join ' '))
# 定位第 9 行（param 块内 $Port 行），打印原始字节
$text = [System.Text.Encoding]::UTF8.GetString($bytes)
$lines = $text -split "`n"
Write-Host ("line 9: " + $lines[8])
$lineBytes = [System.Text.Encoding]::UTF8.GetBytes($lines[8])
Write-Host ("line 9 bytes: " + (($lineBytes | ForEach-Object { $_.ToString('X2') }) -join ' '))
