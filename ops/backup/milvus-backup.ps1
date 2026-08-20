[CmdletBinding()]
param(
    [ValidateSet('export', 'restore')]
    [string]$Operation = 'export'
)

$ErrorActionPreference = 'Stop'
$collections = @(
    'yusi_embedding_collection',
    'yusi_mid_term_memory',
    'yusi_match_profile'
)

if ($collections.Count -ne 3) {
    throw 'Milvus collection contract is incomplete'
}

Write-Output ("DEPLOYMENT-ONLY component=milvus operation={0} collectionCount={1} reason=real-export-import-required" -f
    $Operation, $collections.Count)
