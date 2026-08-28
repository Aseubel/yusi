$targets = 9091, 9092, 9093, 19090, 29090
foreach ($t in $targets) {
    $c = Get-NetTCPConnection -LocalPort $t -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) {
        $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
        Write-Host ("{0} BUSY by {1} (state={2})" -f $t, $p.ProcessName, $c.State)
    } else {
        Write-Host ("{0} free" -f $t)
    }
}
