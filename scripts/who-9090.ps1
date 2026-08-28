$conns = Get-NetTCPConnection -LocalPort 9090 -ErrorAction SilentlyContinue
foreach ($c in $conns) {
    $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($c.OwningProcess)" -ErrorAction SilentlyContinue).CommandLine
    Write-Host ("port {0} state={1} pid={2} proc={3}" -f $c.LocalPort, $c.State, $c.OwningProcess, $p.ProcessName)
    if ($cmd) { Write-Host ("  cmd: " + $cmd.Substring(0, [Math]::Min(200, $cmd.Length))) }
}
