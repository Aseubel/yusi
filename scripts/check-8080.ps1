$conns = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($conns) {
    $conns | ForEach-Object {
        $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
        Write-Host ("LISTEN {0}:{1} pid={2} proc={3}" -f $_.LocalAddress, $_.LocalPort, $_.OwningProcess, $p.ProcessName)
    }
} else {
    Write-Host "nothing listening on 8080"
}
# 探测健康
try {
    $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 5
    Write-Host ("health: " + $r.StatusCode)
} catch {
    Write-Host ("health failed: " + $_.Exception.Message)
}
