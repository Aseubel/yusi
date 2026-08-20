[CmdletBinding()]
param(
    [ValidateSet('inventory', 'restore')]
    [string]$Operation = 'inventory'
)

$ErrorActionPreference = 'Stop'
$protectedObjectClasses = @('image', 'audio', 'attachment')

if ($protectedObjectClasses.Count -ne 3) {
    throw 'OSS protected object contract is incomplete'
}

Write-Output ("DEPLOYMENT-ONLY component=oss operation={0} objectClassCount={1} reason=real-versioning-inventory-restore-required" -f
    $Operation, $protectedObjectClasses.Count)
