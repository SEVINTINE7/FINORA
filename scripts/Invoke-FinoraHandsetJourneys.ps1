[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Sdk,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][string]$Evidence,
    [Parameter(Mandatory)][int]$ApiPid,
    [ValidatePattern('^$|^FINORA-T57-[a-f0-9]{8}$')][string]$ResumeProfile,
    [string]$ResumeArtifacts,
    [switch]$OnlySmsPaste
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$buildTools = Join-Path $Sdk 'build-tools/36.0.0'
$aapt = Join-Path $buildTools 'aapt.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$app = 'lk.finora.app.t57handset'
$test = "$app.test"
$runner = "$test/androidx.test.runner.AndroidJUnitRunner"
$profile = 'FINORA-T57-' + [guid]::NewGuid().ToString('N').Substring(0,8)
if ($ResumeArtifacts -and !$ResumeProfile) { throw 'Retained APKs require the exact prior temporary profile.' }
if ($ResumeProfile) { $profile = $ResumeProfile }
$user = $null
$creationAttempted = $false
$mapped = $false
$cleanup = [Collections.Generic.List[string]]::new()

function Adb {
    $result = & $adb -s $Serial @args
    if ($LASTEXITCODE) { throw "Targeted ADB command failed ($($args[0]))." }
    $result
}
function Wait-State([scriptblock]$condition, [int]$seconds, [string]$failure) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    do {
        if (& $condition) { return }
        Start-Sleep -Seconds 1
    } until ([DateTime]::UtcNow -ge $deadline)
    throw $failure
}
function Current-User { ((Adb shell am get-current-user) -join '').Trim() }
function Users { @(Adb shell pm list users | Where-Object { $_ -match 'UserInfo\{\d+:' } | Sort-Object) }
function User-Ids { @(Users | ForEach-Object { if ($_ -match 'UserInfo\{(\d+):') { $Matches[1] } }) }
function Owned-Users {
    @(Users | ForEach-Object { if ($_ -match ('UserInfo\{(\d+):' + [regex]::Escape($profile) + ':')) { $Matches[1] } })
}
function Package-Paths {
    foreach ($id in 'lk.finora.app.acceptance','lk.finora.app.acceptance.test') {
        Adb shell pm path --user 10 $id
    }
}
function Candidate-Packages([string]$id, [switch]$IncludeUninstalled) {
    $options = @()
    if ($IncludeUninstalled) { $options += '-u' }
    Adb shell pm list packages --user $id @options $app |
        Where-Object { $_ -cmatch ('^package:' + [regex]::Escape($app) + '(?:\.test)?$') }
}
function Confirm-Api {
    $listener = @(Get-NetTCPConnection -State Listen -LocalPort 8080)
    if ($listener.Count -ne 1 -or $listener[0].LocalAddress -ne '127.0.0.1' -or
            $listener[0].OwningProcess -ne $ApiPid -or
            (Invoke-RestMethod -TimeoutSec 3 -Uri 'http://127.0.0.1:8080/actuator/health').status -ne 'UP') {
        throw 'Session-owned loopback API verification failed.'
    }
}
function Confirm-InstalledApk([string]$id, [string]$pkg, [string]$hash, [string]$file) {
    $installed = @(Adb shell pm path --user $id $pkg | Where-Object { $_ -match '^package:/data/app/.+/base\.apk$' })
    if ($installed.Count -ne 1) { throw 'Unexpected installed APK path.' }
    $copy = Join-Path $Evidence $file
    Adb pull ($installed[0] -replace '^package:','') $copy | Out-Null
    if ((Get-FileHash -LiteralPath $copy).Hash -cne $hash) { throw 'Installed and expected APK bytes differ.' }
}
function Apk-Signer([string]$path) {
    $result = & $apksigner verify --print-certs $path
    if ($LASTEXITCODE) { throw 'APK signature verification failed.' }
    $signers = @($result | ForEach-Object {
        if ($_ -match '^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$') {
            $Matches[1].ToLowerInvariant()
        }
    })
    if ($signers.Count -ne 1) { throw 'Expected one APK signer.' }
    $signers[0]
}

if ((Adb get-state) -cne 'device' -or ((Adb shell getprop ro.product.model) -join '').Trim() -cne 'HMD CREST' -or
        ((Adb shell getprop ro.build.version.sdk) -join '').Trim() -cne '34') {
    throw 'Expected the authorized HMD Crest.'
}
$initialUsers = @(User-Ids)
$baseline = $initialUsers
if ($ResumeProfile) {
    $owned = @(Owned-Users)
    if ($owned.Count -ne 1 -or $owned[0] -in '0','10' -or (Current-User) -notin @('0',$owned[0])) {
        throw 'Resume requires the exact prior temporary profile.'
    }
    $user = $owned[0]
    $baseline = @($initialUsers | Where-Object { $_ -cne $user })
} elseif ((Current-User) -cne '0') {
    throw 'New sessions require the original Owner user to be foreground.'
}
$reverse = @(Adb reverse --list | Where-Object { $_.Trim() } | Sort-Object)
$paths = @(Package-Paths)
if (($baseline -join ',') -cne '0,10' -or @(Users | Where-Object { $_ -match 'UserInfo\{10:FINORA-T10-QA:' }).Count -ne 1 -or
        $paths.Count -ne 2 -or @($reverse -match '(^|\s)tcp:8080(\s|$)').Count) {
    throw 'Existing users, Sahan APK paths or USB mapping differ from the required baseline.'
}
foreach ($id in $baseline) {
    if (@(Candidate-Packages $id -IncludeUninstalled:(!$ResumeArtifacts)).Count) {
        throw 'Candidate package already exists for an original user. Refusing installation.'
    }
}
foreach ($pair in @(@($app,$AppApk),@($test,$TestApk))) {
    $badging = & $aapt dump badging $pair[1]
    if ($LASTEXITCODE -or -not ($badging -match ('^package: name=''' + [regex]::Escape($pair[0]) + ''''))) {
        throw 'Unexpected candidate APK identity.'
    }
}
$manifest = & $aapt dump xmltree $TestApk AndroidManifest.xml
if ($LASTEXITCODE -or -not ($manifest -match ('android:targetPackage.*="' + [regex]::Escape($app) + '"'))) {
    throw 'Unexpected instrumentation target.'
}
$signer = Apk-Signer $AppApk
if ((Apk-Signer $TestApk) -cne $signer) { throw 'Instrumentation signer differs from app signer.' }
$sha = @((Get-FileHash -LiteralPath $AppApk).Hash,(Get-FileHash -LiteralPath $TestApk).Hash)
"APP_SHA256=$($sha[0]); TEST_SHA256=$($sha[1]); DEBUG_CERT_SHA256=$signer" | Tee-Object -FilePath (Join-Path $Evidence 'handset-identity.txt')
Confirm-Api

try {
    $creationAttempted = $true
    if (!$ResumeProfile) {
        Adb shell pm create-user $profile | Out-Null
        $owned = @(Owned-Users)
        if ($owned.Count -ne 1 -or $owned[0] -in $baseline) { throw 'Temporary user identity could not be established.' }
        $user = $owned[0]
    }
    if ((Current-User) -cne $user) { Adb shell am switch-user $user | Out-Null }
    Wait-State { (Current-User) -ceq $user -and ((Adb shell am get-started-user-state $user) -join '').Trim() -ceq 'RUNNING_UNLOCKED' } 120 'Temporary Android user is not active and unlocked.'
    $dump = (Adb shell dumpsys user) -join [char]10
    $pattern = 'UserInfo\{' + $user + ':' + [regex]::Escape($profile) + ':[^}]+\}[^\r\n]*(?:\r?\n[ \t]*)?serialNo=(\d+)'
    $identities = [regex]::Matches($dump,$pattern)
    if ($identities.Count -ne 1 -or [long]$identities[0].Groups[1].Value -le 0) { throw 'Temporary Android user serial could not be verified.' }
    $userSerial = $identities[0].Groups[1].Value
    if ($ResumeArtifacts) {
        foreach ($pkg in $app,$test) {
            $old = Join-Path $ResumeArtifacts "$pkg.apk"
            Confirm-InstalledApk $user $pkg (Get-FileHash -LiteralPath $old).Hash "prior-$pkg.apk"
        }
        Adb install -r -t --user $user $TestApk | Out-Null
    } else {
        Adb install --user $user $AppApk | Out-Null
        Adb install -t --user $user $TestApk | Out-Null
    }
    Confirm-InstalledApk $user $app $sha[0] "$app.apk"
    Confirm-InstalledApk $user $test $sha[1] "$test.apk"
    if (-not ((Adb shell pm list instrumentation $app) -match ('^instrumentation:' + [regex]::Escape($runner) + ' \(target=' + [regex]::Escape($app) + '\)$'))) {
        throw 'Unexpected installed instrumentation runner.'
    }
    Confirm-Api
    Adb reverse --no-rebind tcp:8080 tcp:8080 | Out-Null
    $mapped = $true
    $suite = 'lk.finora.app.acceptance.UserJourneyTest'
    $methods = @(
        'registerLoginAndManageFinancialRecordsWithSmsDenied',
        'syntheticPasteFeedsInsightsAndPlanningSupportsSinhala',
        'dailyGuideAndGoalSimulationFromPlanUseTheSignedInOwner',
        'approvedBilingualReplySurvivesCatalogRetirement',
        'adminDashboardOpensWorkingUserAndCategoryManagement',
        'adminListsCreatesAndDisablesStaff',
        'adminManagesAdminsAndPromotesAnIsolatedSyntheticUser',
        'adminCanDiscardAnUnpublishedNsbDraft',
        'adminEditsValidatesPublishesActivatesAndRollsBackNsbDraft'
    )
    if ($OnlySmsPaste) { $methods = @('syntheticPasteFeedsInsightsAndPlanningSupportsSinhala') }
    $failures = @()
    foreach ($method in $methods) {
        if ((Current-User) -cne $user -or (@(Owned-Users) -join ',') -cne $user) { throw 'Temporary user is no longer active.' }
        Confirm-Api
        $argsForTest = @('shell','am','instrument','--user',$user,'-w','-r','-e','finoraIsolatedHandset','true',
            '-e','finoraTestUserSerial',$userSerial,'-e','finoraAdminFixture','true','-e','finoraSupportFixtures','true',
            '-e','class',"$suite#$method",$runner)
        $result = @(Adb @argsForTest)
        $result | Set-Content -LiteralPath (Join-Path $Evidence "$method.log")
        if (($result -match '^OK \(1 test\)') -and -not ($result -match 'INSTRUMENTATION_STATUS_CODE: -[34]')) {
            Write-Host "$method PASS"
        } else {
            $failures += $method
            Write-Host "$method FAILED; see its instrumentation report"
        }
    }
    if ($failures.Count) { throw ('Unverified handset journeys: ' + ($failures -join ', ')) }
} finally {
    if ($mapped) {
        try {
            $actual = @(Adb reverse --list | Where-Object { $_.Trim() } | Sort-Object)
            $ownedMapping = @($actual -match '(^|\s)tcp:8080\s+tcp:8080$')
            $rest = @($actual -notmatch '(^|\s)tcp:8080\s+tcp:8080$')
            if ($ownedMapping.Count -eq 1 -and ($rest -join '|') -ceq ($reverse -join '|')) {
                Adb reverse --remove tcp:8080 | Out-Null
            }
            if ((@(Adb reverse --list | Where-Object { $_.Trim() } | Sort-Object) -join '|') -cne ($reverse -join '|')) {
                throw 'Reverse mappings differ from baseline.'
            }
            Write-Host 'USB reverse baseline restored.'
        } catch { $cleanup.Add('reverse mapping') }
    }
    if ($creationAttempted) {
        try {
            $owned = @(Owned-Users)
            if ($owned.Count -eq 1 -and $owned[0] -notin $baseline -and (!$user -or $user -ceq $owned[0])) {
                $user = $owned[0]
                if ((Current-User) -ceq $user) {
                    Adb shell am switch-user 0 | Out-Null
                    Wait-State { (Current-User) -ceq '0' } 60 'Original user did not return.'
                }
                if ((Current-User) -cne '0') { throw 'Refusing user removal outside the original Owner user.' }
                Adb shell am stop-user -w -f $user | Out-Null
                Wait-State {
                    if ($user -notin @(User-Ids)) { return $true }
                    if ((Current-User) -cne '0' -or (@(Owned-Users) -join ',') -cne $user) {
                        throw 'Temporary user identity changed before removal.'
                    }
                    # The HMD Crest can reject removal while its user-stop transition settles.
                    try { Adb shell pm remove-user $user | Out-Null } catch { return $false }
                    return $user -notin @(User-Ids)
                } 60 'Temporary user removal could not be verified.'
                Write-Host 'Temporary Android user removed.'
            } elseif ($owned.Count -or ($user -and $user -in @(User-Ids))) {
                throw 'Refusing to remove a user with ambiguous ownership.'
            }
        } catch { Write-Warning $_.Exception.Message; $cleanup.Add('temporary user') }
        try {
            if ((Current-User) -cne '0' -or ((@(User-Ids) -join ',') -cne ($baseline -join ',')) -or
                    ((@(Package-Paths) -join '|') -cne ($paths -join '|'))) {
                throw 'Original Android users or Sahan APK paths differ.'
            }
            foreach ($id in $baseline) {
                if (@(Candidate-Packages $id -IncludeUninstalled).Count) { throw 'Candidate package remains on original user.' }
            }
            Write-Host 'Owner, original users and Sahan APK paths verified.'
        } catch { $cleanup.Add('Android baseline verification') }
    }
    if ($cleanup.Count) { throw ('Handset cleanup unverified: ' + ($cleanup -join ', ')) }
}
