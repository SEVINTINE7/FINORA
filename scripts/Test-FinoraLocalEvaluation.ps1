[CmdletBinding()]
param(
    [string]$LauncherPath = (Join-Path $PSScriptRoot 'Start-FinoraLocalEvaluation.ps1'),
    [Parameter(DontShow)][switch]$ComponentChild
)

# Component checks only: no Windows services, database, emulator or real ADB commands.
# The child owns every synthetic environment change. The launcher is never dot-sourced.
$ErrorActionPreference = 'Stop'
if (-not $ComponentChild) {
    $hostExecutable = (Get-Process -Id $PID).Path
    & $hostExecutable -NoLogo -NoProfile -NonInteractive -File $PSCommandPath -LauncherPath $LauncherPath -ComponentChild
    exit $LASTEXITCODE
}

$script:failures = 0
function Assert-Component([bool]$Condition, [string]$Label) {
    if ($Condition) { Write-Output "PASS $Label" }
    else { $script:failures++; Write-Output "FAIL $Label" }
}

function Get-AssignmentName($Statement) {
    if ($Statement -is [System.Management.Automation.Language.AssignmentStatementAst] -and
            $Statement.Left -is [System.Management.Automation.Language.VariableExpressionAst]) {
        return $Statement.Left.VariablePath.UserPath
    }
    return ''
}

function New-ComponentBlock($Statements, [string[]]$AllowedCommands) {
    foreach ($statement in @($Statements)) {
        foreach ($command in $statement.FindAll({
            param($node) $node -is [System.Management.Automation.Language.CommandAst]
        }, $true)) {
            $name = $command.GetCommandName()
            $mockAdb = -not $name -and $command.CommandElements[0].Extent.Text -eq '$adb'
            if (-not $mockAdb -and $name -notin $AllowedCommands) {
                throw 'Selected component contains an unexpected command; execution refused.'
            }
        }
    }
    $source = (@($Statements) | ForEach-Object { $_.Extent.Text }) -join ([Environment]::NewLine)
    return [scriptblock]::Create($source)
}

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path -LiteralPath $LauncherPath).Path, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Launcher PowerShell syntax is invalid.' }
$top = @($ast.EndBlock.Statements)
$outer = @($top | Where-Object { $_ -is [System.Management.Automation.Language.TryStatementAst] })
if ($outer.Count -ne 1 -or -not $outer[0].Finally) { throw 'Expected one outer launcher try/finally.' }
$outer = $outer[0]

# Select actual capture statements, including dynamically discovered connection/profile overrides.
$capture = @($top | Where-Object {
    (Get-AssignmentName $_) -in @('connectionOverrides','profileOverrides','envNames','previous') -or
    ($_ -is [System.Management.Automation.Language.ForEachStatementAst] -and
        $_.Condition.Extent.Text -eq '$envNames')
})
$environmentCommands = @('Get-ChildItem','Where-Object','ForEach-Object','Select-Object','Join-Path','Remove-Item')
$captureBlock = New-ComponentBlock $capture $environmentCommands

# Execute only the startup prefix before the first statement containing Start-Process.
$startup = @()
$foundProcessBoundary = $false
foreach ($statement in $outer.Body.Statements) {
    if ($statement.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.CommandAst] -and
            $node.GetCommandName() -eq 'Start-Process'
    }, $true).Count) { $foundProcessBoundary = $true; break }
    $startup += $statement
}
if (-not $foundProcessBoundary) { throw 'No process-start boundary; component selection refused.' }
$startupBlock = New-ComponentBlock $startup $environmentCommands

$pinNames = @('url','env:SPRING_DATASOURCE_URL','env:SPRING_DATASOURCE_USERNAME',
    'env:SPRING_DATASOURCE_PASSWORD','env:SPRING_FLYWAY_URL','env:SPRING_FLYWAY_USER',
    'env:SPRING_FLYWAY_PASSWORD','env:TEST_DB_URL','env:TEST_DB_USER','env:TEST_DB_PASS',
    'env:DB_URL','env:DB_USER','env:DB_PASS')
$pins = @($outer.Body.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst]
}, $true) | Where-Object { (Get-AssignmentName $_) -in $pinNames } |
    Sort-Object { $_.Extent.StartOffset })
$pinBlock = New-ComponentBlock $pins $environmentCommands
$restore = @($outer.Finally.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.ForEachStatementAst] -and
        $node.Condition.Extent.Text -eq '$envNames'
}, $true))
if ($restore.Count -ne 1) { throw 'Expected exactly one environment-restoration component.' }
$restoreBlock = New-ComponentBlock $restore $environmentCommands

function Test-EnvironmentComponent {
    $seed = @{
        SPRING_DATASOURCE_URL = 'jdbc:mysql://foreign.invalid/fixture'
        SPRING_DATASOURCE_USERNAME = 'synthetic-original-user'
        SPRING_DATASOURCE_PASSWORD = 'synthetic-original-password'
        SPRING_DATASOURCE_HIKARI_JDBC_URL = 'jdbc:mysql://foreign.invalid/hikari'
        SPRING_DATASOURCE_HIKARI_DATASOURCEPROPERTIES_SERVERNAME = 'foreign.invalid'
        SPRING_DATASOURCE_JNDI_NAME = 'java:comp/env/synthetic-foreign'
        SPRING_FLYWAY_URL = 'jdbc:mysql://foreign.invalid/migrations'
        SPRING_FLYWAY_USER = 'synthetic-original-migration-user'
        SPRING_FLYWAY_SCHEMAS = 'synthetic_foreign'
        SPRING_FLYWAY_LOCATIONS = 'filesystem:synthetic-foreign'
        MAVEN_OPTS = '-Dspring.datasource.url=jdbc:mysql://foreign.invalid/options'
        MAVEN_ARGS = '-Dspring.flyway.url=jdbc:mysql://foreign.invalid/arguments'
        MAVEN_DEBUG_OPTS = '-Dspring.datasource.url=jdbc:mysql://foreign.invalid/debug'
        JVM_CONFIG_MAVEN_OPTS = '-Dspring.datasource.url=jdbc:mysql://foreign.invalid/jvm'
        MAVEN_BASEDIR = 'synthetic-foreign-maven-base'
        MAVEN_SKIP_RC = 'synthetic-original-rc-setting'
        FINORA_FIREBASE_CREDENTIALS = 'synthetic-direct-firebase'
        FIREBASE_CREDENTIALS = 'synthetic-firebase'
        GOOGLE_APPLICATION_CREDENTIALS = 'synthetic-google'
        GROQ_API_KEY = 'synthetic-only-groq'
        GEMINI_API_KEY = 'synthetic-only-gemini'
        FINORA_LLM_ENABLED = 'true'
        SPRING_APPLICATION_JSON = '{"synthetic":"override"}'
        SPRING_CONFIG_NAME = 'synthetic-foreign-config'
        SPRING_PROFILES_ACTIVE = 'synthetic-foreign-profile'
        SPRING_PROFILES_INCLUDE = 'synthetic-extra-profile'
        JDK_JAVA_OPTIONS = '-Dsynthetic.fixture=true'
    }
    foreach ($name in $seed.Keys) {
        [Environment]::SetEnvironmentVariable($name, $seed[$name], 'Process')
    }
    foreach ($name in 'SPRING_FLYWAY_PASSWORD','MAVEN_USER_HOME','TEST_DB_PASS') {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    $before = [Environment]::GetEnvironmentVariables('Process')
    $JavaHome = Join-Path ([IO.Path]::GetTempPath()) 'finora-synthetic-jdk'
    $MavenUserHome = Join-Path ([IO.Path]::GetTempPath()) 'finora-synthetic-maven'
    $DbPort = 3339
    $dbUser = 'synthetic_loopback_user'
    $dbPass = 'synthetic-loopback-password'
    $Mode = 'Verify'
    $previous = @{}
    $envNames = @()
    $connectionOverrides = @()
    $profileOverrides = @()
    . $captureBlock
    try {
        . $startupBlock
        $scrubbed = @('MAVEN_OPTS','MAVEN_ARGS','MAVEN_DEBUG_OPTS','JVM_CONFIG_MAVEN_OPTS','MAVEN_BASEDIR',
            'FINORA_FIREBASE_CREDENTIALS','FIREBASE_CREDENTIALS','GOOGLE_APPLICATION_CREDENTIALS',
            'GROQ_API_KEY','GEMINI_API_KEY','SPRING_APPLICATION_JSON','JDK_JAVA_OPTIONS','SPRING_PROFILES_INCLUDE') +
            @($seed.Keys | Where-Object { $_ -like 'SPRING_DATASOURCE_*' -or $_ -like 'SPRING_FLYWAY_*' })
        $remaining = @($scrubbed | Where-Object {
            $null -ne [Environment]::GetEnvironmentVariable($_, 'Process')
        })
        Assert-Component ($remaining.Count -eq 0) 'environment overrides scrubbed before process startup'
        Assert-Component ($env:FINORA_LLM_ENABLED -ceq 'false' -and $env:MAVEN_SKIP_RC -ceq '1' -and
            $env:SPRING_CONFIG_NAME -ceq 'application' -and $env:SPRING_PROFILES_ACTIVE -ceq 'test') 'provider disabled and Maven/config/profile pinned'
        . $pinBlock
        $expectedUrl = 'jdbc:mysql://127.0.0.1:3339/finora_test'
        $expected = @{
            SPRING_DATASOURCE_URL = $expectedUrl; SPRING_DATASOURCE_USERNAME = $dbUser
            SPRING_DATASOURCE_PASSWORD = $dbPass; SPRING_FLYWAY_URL = $expectedUrl
            SPRING_FLYWAY_USER = $dbUser; SPRING_FLYWAY_PASSWORD = $dbPass
            TEST_DB_URL = $expectedUrl; TEST_DB_USER = $dbUser; TEST_DB_PASS = $dbPass
            DB_URL = $expectedUrl; DB_USER = $dbUser; DB_PASS = $dbPass
        }
        $wrongPins = @($expected.Keys | Where-Object {
            [Environment]::GetEnvironmentVariable($_, 'Process') -cne $expected[$_]
        })
        Assert-Component ($wrongPins.Count -eq 0) 'application and migration endpoints pinned to synthetic loopback'
    } finally {
        . $restoreBlock
        $after = [Environment]::GetEnvironmentVariables('Process')
        $changed = @($before.Keys | Where-Object { $before[$_] -cne $after[$_] })
        $extra = @($after.Keys | Where-Object { -not $before.Contains($_) })
        Assert-Component ($changed.Count -eq 0 -and $extra.Count -eq 0) 'complete original child environment restored including absent values'
    }
}

# Select only the emulator branch of outer cleanup; all device/process operations below are mocks.
$emulatorCleanup = @($outer.Finally.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.IfStatementAst] -and
        $node.Clauses[0].Item1.Extent.Text -match '^\$emulator -and -not \$emulator\.HasExited$'
}, $true))
if ($emulatorCleanup.Count -ne 1) { throw 'Expected exactly one outer emulator-cleanup component.' }
$cleanupBlock = New-ComponentBlock $emulatorCleanup @('Invoke-Device','Select-Object','Write-Warning','Stop-Process')

function Test-CleanupComponent([string]$Scenario) {
    $WarningPreference = 'SilentlyContinue'
    $calls = [Collections.Generic.List[string]]::new()
    $AdbPort = 5039
    $serial = 'emulator-5582'
    $avd = 'FINORA_T55_Evaluation'
    $appId = 'lk.finora.app.acceptance'
    $permissionChanged = $true
    $mapped = $true
    $LASTEXITCODE = 0
    $emulator = [pscustomobject]@{ Id = 424242; HasExited = $false }
    $emulator | Add-Member ScriptMethod WaitForExit {
        param($Milliseconds)
        if ($Milliseconds -ne 5000) { throw 'Unexpected process wait bound.' }
        return $false
    }
    $adb = {
        $calls.Add('identity')
        if (($args -join ' ') -cne "-P $AdbPort -s $serial emu avd name") {
            $calls.Add('foreign-adb-command')
            throw 'Unscoped or unexpected mocked ADB command.'
        }
        if ($Scenario -eq 'identity mismatch') { return 'OTHER_AVD' }
        return $avd
    }
    function Invoke-Device {
        $command = $args -join ' '
        switch -Exact ($command) {
            "shell pm grant --user 0 $appId android.permission.READ_SMS" {
                $calls.Add('permission')
                if ($Scenario -in @('permission failure','both failures')) { throw 'Synthetic permission failure.' }
            }
            "shell dumpsys package $appId" { return 'android.permission.READ_SMS: granted=true' }
            'reverse --remove tcp:8080' {
                $calls.Add('reverse')
                if ($Scenario -in @('reverse failure','both failures')) { throw 'Synthetic reverse failure.' }
            }
            'reverse --list' { return '' }
            'emu kill' {
                $calls.Add('console')
                if ($Scenario -eq 'console failure') { throw 'Synthetic console failure.' }
                $emulator.HasExited = $true
            }
            default {
                $calls.Add('foreign-adb-command')
                throw 'Unexpected mocked device command.'
            }
        }
    }
    function Stop-Process {
        [CmdletBinding()] param([object]$InputObject)
        if (-not [object]::ReferenceEquals($InputObject, $emulator)) {
            $calls.Add('foreign-process-stop')
            throw 'Only the owned synthetic process may be stopped.'
        }
        $calls.Add('process-stop')
        $emulator.HasExited = $true
    }
    $completed = $true
    try { . $cleanupBlock } catch { $completed = $false }
    $identityMismatch = $Scenario -eq 'identity mismatch'
    $expectedActions = if ($identityMismatch) {
        $calls.Contains('process-stop') -and -not $calls.Contains('permission') -and
            -not $calls.Contains('reverse') -and -not $calls.Contains('console')
    } else {
        $calls.Contains('permission') -and $calls.Contains('reverse') -and $calls.Contains('console') -and
            ($Scenario -ne 'console failure' -or $calls.Contains('process-stop'))
    }
    Assert-Component ($completed -and $expectedActions -and $emulator.HasExited -and
        -not $calls.Contains('foreign-adb-command') -and -not $calls.Contains('foreign-process-stop')) "mocked emulator cleanup: $Scenario"
}

Test-EnvironmentComponent
foreach ($scenario in 'normal','permission failure','reverse failure','both failures','console failure','identity mismatch') {
    Test-CleanupComponent $scenario
}
if ($script:failures) { Write-Output "FAIL $script:failures component checks"; exit 1 }
Write-Output 'PASS 10 component checks; environment child process and mocked emulator cleanup only.'
exit 0
