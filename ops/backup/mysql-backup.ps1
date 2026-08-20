[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$DatabaseName,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputDirectory,

    [ValidatePattern('^[A-Za-z0-9._-]{1,128}$')]
    [string]$BackupId = ("mysql-" + (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')),

    [string]$MysqlHost = $env:YUSI_MYSQL_HOST,
    [int]$MysqlPort = 3306,
    [string]$MysqlUser = $env:YUSI_MYSQL_USER
)

$ErrorActionPreference = 'Stop'
$manifestSchemaVersion = 'v1'
$toolVersion = 'mysql-backup.ps1-v1'

function Format-Utc([DateTimeOffset]$value) {
    return $value.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
}

function Require-Value([string]$name, [string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$name must be supplied by an explicit parameter or deployment environment"
    }
}

Require-Value 'MysqlHost' $MysqlHost
Require-Value 'MysqlUser' $MysqlUser
$mysqlPassword = [Environment]::GetEnvironmentVariable('YUSI_MYSQL_PASSWORD', 'Process')
Require-Value 'YUSI_MYSQL_PASSWORD' $mysqlPassword

if ($DatabaseName -in @('mysql', 'information_schema', 'performance_schema', 'sys')) {
    throw 'system databases are not valid backup targets'
}

$outputDirectoryInfo = New-Item -ItemType Directory -Path $OutputDirectory -Force
$sourceTimestamp = [DateTimeOffset]::UtcNow
$dumpPath = Join-Path $outputDirectoryInfo.FullName ($BackupId + '.sql')
$manifestPath = Join-Path $outputDirectoryInfo.FullName ($BackupId + '.manifest.json')
$previousMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $mysqlPassword, 'Process')

    $dumpArguments = @(
        "--host=$MysqlHost",
        "--port=$MysqlPort",
        "--user=$MysqlUser",
        '--single-transaction',
        '--routines',
        '--events',
        '--triggers',
        '--hex-blob',
        '--set-gtid-purged=OFF',
        $DatabaseName
    )
    & mysqldump @dumpArguments > $dumpPath
    if ($LASTEXITCODE -ne 0) {
        throw 'mysqldump exited with a non-zero status'
    }

    $tableCountArguments = @(
        "--host=$MysqlHost",
        "--port=$MysqlPort",
        "--user=$MysqlUser",
        '--batch',
        '--skip-column-names',
        '--execute',
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DatabaseName'"
    )
    $tableCountRaw = & mysql @tableCountArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'table count query exited with a non-zero status'
    }
    $tableCount = [int64](([string]$tableCountRaw).Trim())

    $artifactHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath).Hash.ToLowerInvariant()
    $artifactBytes = (Get-Item -LiteralPath $dumpPath).Length
    $createdTimestamp = [DateTimeOffset]::UtcNow
    $manifest = [ordered]@{
        backupId = $BackupId
        component = 'mysql'
        sourceDataTimestampUtc = Format-Utc $sourceTimestamp
        createdAtUtc = Format-Utc $createdTimestamp
        artifactSha256 = $artifactHash
        artifactBytes = [int64]$artifactBytes
        schemaVersion = $manifestSchemaVersion
        toolVersion = $toolVersion
        counts = [ordered]@{ tables = $tableCount }
        retentionClass = 'standard'
        restorePoint = Format-Utc $sourceTimestamp
    }
    $json = $manifest | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText($manifestPath, $json, [System.Text.UTF8Encoding]::new($false))
    Write-Output ("BACKUP_CREATED component=mysql backupId={0} bytes={1} tables={2}" -f
        $BackupId, $artifactBytes, $tableCount)
}
catch {
    Write-Error 'MySQL backup failed; inspect the deployment job status without printing command secrets or data.'
    exit 1
}
finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousMysqlPassword, 'Process')
}
