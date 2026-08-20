[CmdletBinding()]
param(
    [ValidateSet('snapshot', 'restore')]
    [string]$Operation = 'snapshot'
)

$ErrorActionPreference = 'Stop'
$keyFamilyClasses = [ordered]@{
    'rebuildable-cache' = @('yusi:langchain:*', 'yusi:chunk:*', 'yusi:md5:*')
    'security-state' = @('yusi:auth:*')
    'reconcile-with-mysql' = @('yusi:usage:*')
    'review-before-restore' = @('yusi:violation:count:*')
    'rebuildable-runtime' = @('yusi:model:state:instances')
    'non-restorable-channel' = @('yusi:model:state:channel', 'yusi:model:config:channel')
    'restore-from-mysql' = @('yusi:model:runtime:config')
}

if ($keyFamilyClasses.Count -ne 7) {
    throw 'Redis key-family contract is incomplete'
}

Write-Output ("DEPLOYMENT-ONLY component=redis operation={0} keyFamilyClassCount={1} reason=real-snapshot-restore-required" -f
    $Operation, $keyFamilyClasses.Count)
