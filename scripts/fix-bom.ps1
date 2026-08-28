$b = [System.IO.File]::ReadAllBytes('d:\develop\projects\yusi\run-backend.ps1')
if ($b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF -and $b[3] -eq 0xEF) {
    # 双 BOM：剥掉一个
    $fixed = New-Object byte[] ($b.Length - 3)
    [Array]::Copy($b, 3, $fixed, 0, $fixed.Length)
    [System.IO.File]::WriteAllBytes('d:\develop\projects\yusi\run-backend.ps1', $fixed)
    Write-Host 'double BOM fixed -> single BOM'
} else {
    Write-Host 'no double BOM'
}
