[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$DumpPath,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ManifestPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$TargetDatabase,

    [string]$MysqlHost = $env:YUSI_MYSQL_HOST,
    [int]$MysqlPort = 3306,
    [string]$MysqlUser = $env:YUSI_MYSQL_USER,
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'
$forbiddenManifestFields = @('userId', 'query', 'content', 'token', 'objectKey')

function Require-Value([string]$name, [string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$name must be supplied by an explicit parameter or deployment environment"
    }
}

function Assert-NoForbiddenManifestFields([object]$node) {
    if ($null -eq $node) {
        return
    }
    if ($node -is [System.Management.Automation.PSCustomObject]) {
        foreach ($property in $node.PSObject.Properties) {
            if ($forbiddenManifestFields -contains $property.Name) {
                throw 'manifest contains a forbidden field name'
            }
            Assert-NoForbiddenManifestFields $property.Value
        }
        return
    }
    if ($node -is [System.Collections.IEnumerable] -and $node -isnot [string]) {
        foreach ($item in $node) {
            Assert-NoForbiddenManifestFields $item
        }
    }
}

function Get-Manifest([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'manifest file does not exist'
    }
    try {
        return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
    }
    catch {
        throw 'manifest is not valid JSON'
    }
}

function Assert-Manifest([object]$manifest, [string]$artifactPath) {
    Assert-NoForbiddenManifestFields $manifest
    foreach ($field in @('backupId', 'component', 'sourceDataTimestampUtc', 'createdAtUtc',
            'artifactSha256', 'artifactBytes', 'schemaVersion', 'toolVersion', 'counts',
            'retentionClass', 'restorePoint')) {
        if ($null -eq $manifest.PSObject.Properties[$field]) {
            throw 'manifest is missing a required field'
        }
    }
    if ($manifest.component -ne 'mysql' -or $manifest.schemaVersion -ne 'v1') {
        throw 'manifest component or schema version is not supported'
    }
    if ([string]$manifest.artifactSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'manifest checksum is not exactly 64 hexadecimal characters'
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash
    if ($actualHash -ine [string]$manifest.artifactSha256) {
        throw 'dump checksum does not match manifest'
    }
}

function Invoke-Scalar([string[]]$arguments, [string]$sql) {
    $result = & mysql @arguments --execute $sql
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL invariant query failed'
    }
    return [int64](([string]$result).Trim())
}

if ($TargetDatabase -in @('yusi', 'mysql', 'information_schema', 'performance_schema', 'sys')) {
    throw 'restore target must be an explicit isolated non-production database'
}
if (-not (Test-Path -LiteralPath $DumpPath -PathType Leaf)) {
    throw 'dump file does not exist'
}

$manifest = Get-Manifest $ManifestPath
Assert-Manifest $manifest $DumpPath

if (-not $Execute) {
    Write-Output 'DEPLOYMENT-ONLY component=mysql reason=execution-not-requested'
    exit 0
}

Require-Value 'MysqlHost' $MysqlHost
Require-Value 'MysqlUser' $MysqlUser
$mysqlPassword = [Environment]::GetEnvironmentVariable('YUSI_MYSQL_PASSWORD', 'Process')
Require-Value 'YUSI_MYSQL_PASSWORD' $mysqlPassword
$previousMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $mysqlPassword, 'Process')
    $mysqlArguments = @("--host=$MysqlHost", "--port=$MysqlPort", "--user=$MysqlUser")
    $createDatabaseSql = 'CREATE DATABASE IF NOT EXISTS `' + $TargetDatabase + '` '
        + 'CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
    & mysql @mysqlArguments --execute $createDatabaseSql
    if ($LASTEXITCODE -ne 0) {
        throw 'target database creation failed'
    }

    Get-Content -Raw -LiteralPath $DumpPath | & mysql @mysqlArguments --database=$TargetDatabase
    if ($LASTEXITCODE -ne 0) {
        throw 'dump restore failed'
    }

    $targetArguments = $mysqlArguments + "--database=$TargetDatabase"
    $diaryOrphans = Invoke-Scalar $targetArguments 'SELECT COUNT(*) FROM diary d LEFT JOIN `user` u ON u.user_id = d.user_id WHERE u.user_id IS NULL'
    $relationOrphans = Invoke-Scalar $targetArguments 'SELECT COUNT(*) FROM life_graph_relation r LEFT JOIN life_graph_entity s ON s.id = r.source_id LEFT JOIN life_graph_entity t ON t.id = r.target_id WHERE s.id IS NULL OR t.id IS NULL'
    if ($diaryOrphans -ne 0 -or $relationOrphans -ne 0) {
        throw 'application-level orphan check failed'
    }
    Write-Output ("RESTORE_VERIFIED component=mysql target={0} diaryOrphans={1} relationOrphans={2}" -f
        $TargetDatabase, $diaryOrphans, $relationOrphans)
}
catch {
    Write-Error 'MySQL restore rehearsal failed; inspect the deployment job status without printing data or secrets.'
    exit 1
}
finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousMysqlPassword, 'Process')
}
