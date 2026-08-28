$e = $null
$t = $null
[System.Management.Automation.Language.Parser]::ParseFile('d:\develop\projects\yusi\run-benchmark.ps1', [ref]$t, [ref]$e) | Out-Null
if ($e.Count) {
    $e | ForEach-Object { Write-Host ("ERR line {0}: {1}" -f $_.Extent.StartLineNumber, $_.Message) }
    exit 1
}
Write-Host 'parse OK'
