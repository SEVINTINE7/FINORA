param([Parameter(Mandatory=$true)][string]$MavenRepository)

$previous = $env:FINORA_D44_APPROVED
$previousLimit = $env:FINORA_D44_HTTP_LIMIT
$api = Split-Path $PSScriptRoot -Parent
function Assert-GuardRejection([string]$Expected) {
    $log = Join-Path ([IO.Path]::GetTempPath()) ("finora-d44-guard-" + [guid]::NewGuid() + '.log')
    & .\mvnw.cmd -B -o "-Dmaven.repo.local=$MavenRepository" '-Dtest=LiveProviderAcceptanceIT' -l $log test
    $mavenExit = $LASTEXITCODE
    if ($mavenExit -ne 1 -or -not (Select-String -LiteralPath $log -SimpleMatch -Pattern $Expected -Quiet)) {
        throw "Did not observe expected guard rejection. See $log"
    }
    Write-Output "Observed '$Expected' before HTTP (Maven exit $mavenExit). Evidence: $log"
}
Push-Location $api
try {
    Remove-Item Env:FINORA_D44_APPROVED -ErrorAction SilentlyContinue
    Assert-GuardRejection 'Explicit D44 authorization is required'
    $env:FINORA_D44_APPROVED = 'true'
    $env:FINORA_D44_HTTP_LIMIT = '3'
    Assert-GuardRejection 'Approved HTTP limit must cover both adapters and their retries'
} finally {
    $env:FINORA_D44_APPROVED = $previous
    $env:FINORA_D44_HTTP_LIMIT = $previousLimit
    Pop-Location
}
