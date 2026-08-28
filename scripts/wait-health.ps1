param([string]$Url = "http://127.0.0.1:8080/actuator/health", [int]$Retries = 12, [int]$IntervalSec = 5)
for ($i = 1; $i -le $Retries; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 10
        Write-Host ("[{0}/{1}] {2} {3}" -f $i, $Retries, $resp.StatusCode, $resp.Content)
        if ($resp.StatusCode -eq 200) { exit 0 }
    } catch {
        Write-Host ("[{0}/{1}] not ready: {2}" -f $i, $Retries, $_.Exception.Message)
    }
    Start-Sleep -Seconds $IntervalSec
}
exit 1
