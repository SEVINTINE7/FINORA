[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$AdbPath,
    [Parameter(Mandatory)][ValidatePattern('^(?:[A-Za-z0-9]+|emulator-5582)$')][string]$Serial,
    [Parameter(Mandatory)][ValidateRange(1,65535)][int]$UserId,
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*$')][string]$UserSerial,
    [Parameter(Mandatory)][SecureString]$WorkerPassword,
    [Parameter(Mandatory)][string]$Evidence,
    [Parameter(Mandatory)][string]$TraceProcessor,
    [switch]$EmulatorPreflight
)
$ErrorActionPreference = 'Stop'
$app = 'lk.finora.app.t57handset'
$runner = "$app.test/androidx.test.runner.AndroidJUnitRunner"
$prefix = 'finora-t55-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$suite = 'lk.finora.app.acceptance.PerformancePilotTest#measure'
$sql = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/t55-performance/handset-redesign-display.sql'
function Target {
    $rows = & $AdbPath -s $Serial @args
    if ($LASTEXITCODE) { throw "Scoped performance ADB command failed: $($args[0])." }
    $rows
}
function Confirm-Profile {
    $active = ((Target shell am get-current-user) -join '').Trim()
    $users = @(Target shell pm list users | Where-Object { $_ -match ('UserInfo\{'+$UserId+':FINORA-T57-[a-f0-9]{8}:') })
    if ($active -cne [string]$UserId -or $users.Count -ne 1) { throw 'Performance profile ownership changed.' }
}
if ($EmulatorPreflight -ne ($Serial -ceq 'emulator-5582') -or
    ($EmulatorPreflight -and ($env:ADB_SERVER_PORT -ne '5039' -or
        (@(Target emu avd name)[0]).Trim() -cne 'FINORA_T55_Evaluation'))) {
    throw 'Unexpected performance target.'
}
Confirm-Profile
$fingerprint = ((Target shell getprop ro.build.fingerprint) -join '').Trim()
if ($fingerprint -cnotmatch '^[A-Za-z0-9._/:+=,-]+$') { throw 'Invalid handset fingerprint.' }
$metadata = [ordered]@{
    model=((Target shell getprop ro.product.model) -join '').Trim()
    api=((Target shell getprop ro.build.version.sdk) -join '').Trim()
    fingerprint=$fingerprint
    board=((Target shell getprop ro.product.board) -join '').Trim()
    resolution=((Target shell wm size) -join '; ').Trim()
    memory=(@(Target shell head -n 1 /proc/meminfo) -join '').Trim()
    classification=if ($EmulatorPreflight) {'Dedicated emulator; lifecycle-only evidence'} else {'HMD Crest; formal mid-range classification unapproved'}
    cohort=if ($EmulatorPreflight) {'Guarded recovery, Insights rehearsal and full capture, and five external cold launches'} else {'Targeted warm Home, Insights and Budgets; three trace checks, five warm-ups and 30 measured attempts per qualifying route'}
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Evidence 'device.json')
$cfg = @"
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
buffers { size_kb: 8192 fill_policy: RING_BUFFER }
duration_ms: 600000
write_into_file: true
file_write_period_ms: 1000
data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
  ftrace_events: "ftrace/print"
  atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "am"
  atrace_apps: "$app"
} } }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 1 } }
data_sources { config { name: "linux.process_stats" } }
"@
$rows = [Collections.Generic.List[object]]::new()
function Send-WorkerPassword {
    Confirm-Profile
    $plain = [Net.NetworkCredential]::new('', $WorkerPassword).Password
    try {
        $remoteInput = "run-as $app --user $UserId sh -c 'umask 077; mkdir -p files; cat > files/t55-performance-input'"
        $null = $plain | & $AdbPath -s $Serial shell -T $remoteInput
        if ($LASTEXITCODE) { throw 'Synthetic credential could not be delivered to the test-only app.' }
    } finally { $plain = $null }
}
function Capture-Cohort([string]$name, [string]$route, [bool]$calibration, [switch]$ExternalCold) {
    Confirm-Profile
    $remote = "/data/misc/perfetto-traces/$prefix-$name.perfetto-trace"
    $path = Join-Path $Evidence "$name.perfetto-trace"
    $configuration = Join-Path $Evidence "$name-perfetto.pbtxt"
    $cfg | Set-Content -LiteralPath $configuration -Encoding utf8NoBOM
    $traceProcess = Start-Process $AdbPath -ArgumentList '-s',$Serial,'shell','-T','perfetto','--txt','-c','-','-o',$remote -PassThru -WindowStyle Hidden -RedirectStandardInput $configuration -RedirectStandardOutput (Join-Path $Evidence "$name-perfetto.log") -RedirectStandardError (Join-Path $Evidence "$name-perfetto-error.log")
    $timingStatus = [Collections.Generic.List[string]]::new()
    $script:captureClean = $false
    $executionFailure = $null
    try {
        Start-Sleep -Seconds 3
        if ($traceProcess.HasExited) { throw 'Perfetto exited before the first measurement.' }
        Confirm-Profile
        if ($name -eq 'preflight-failure') { throw 'Injected emulator capture-body failure.' }
        if ($ExternalCold) {
            foreach ($attempt in 1..5) {
                Confirm-Profile
                Target shell am force-stop --user $UserId $app | Out-Null
                $launch = @(& $AdbPath -s $Serial shell am start -W --user $UserId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$app/lk.finora.app.ui.login.LoginActivity")
                $result = $LASTEXITCODE
                Set-Content -LiteralPath (Join-Path $Evidence "external-cold-$attempt.log") -Value (@("ADB_EXIT=$result")+$launch)
                if ($result -or !($launch -match '^Status: ok$') -or !($launch -match '^LaunchState: COLD$')) {
                    $executionFailure = 'Android activity manager did not confirm every cold launch.'
                }
                Start-Sleep -Seconds 5
            }
        } else {
            $invocation = @('shell','am','instrument','--user',"$UserId",'-w','-r','-e','class',$suite,
                '-e','finoraPerformancePilot','true',
                '-e',$(if ($EmulatorPreflight) {'finoraPerformanceEmulatorPreflight'} else {'finoraPerformanceHandset'}),'true',
                '-e','finoraBuildFingerprint',$fingerprint,'-e','finoraTestUserSerial',$UserSerial)
            Send-WorkerPassword
            $invocation += @('-e','finoraTraceRoute',$route)
            if ($calibration) { $invocation += @('-e','finoraTraceCheck','true') }
            $testLog = Join-Path $Evidence "$name-test-1.log"
            $test = Start-Process $AdbPath -ArgumentList (@('-s',$Serial) + $invocation + $runner) -PassThru -WindowStyle Hidden `
                -RedirectStandardOutput $testLog -RedirectStandardError (Join-Path $Evidence "$name-test-error.log")
            if (!$test.WaitForExit(240000)) {
                Confirm-Profile
                Target shell am force-stop --user $UserId $app | Out-Null
                if (!$test.WaitForExit(5000)) { Stop-Process -InputObject $test }
                $executionFailure = 'Instrumented route exceeded its transport deadline.'
            } elseif ($test.ExitCode -ne 0) {
                $executionFailure = "Instrumented route transport exit $($test.ExitCode)."
            }
            $testResult = @(Get-Content -LiteralPath $testLog)
            foreach ($status in ($testResult -cmatch '^INSTRUMENTATION_STATUS: finoraTiming=')) { $timingStatus.Add($status) }
            if (!($testResult -match '^OK \(1 test\)')) { $executionFailure ??= 'Instrumented route did not pass.' }
        }
        if ($traceProcess.HasExited) { throw 'Perfetto ended before the cohort completed.' }
    } catch {
        $executionFailure = $_.Exception.Message
    } finally {
        if (!$traceProcess.HasExited) {
            $needle = '^\s*([1-9][0-9]*)\s+.*\bperfetto\b.*' + [regex]::Escape($remote) + '\s*$'
            # Quote the field list so the PowerShell function passes ADB one argument.
            $traceOwners = @(Target shell ps -A -o 'PID,CMDLINE' | ForEach-Object { if ($_ -cmatch $needle) { $Matches[1] } })
            if ($traceOwners.Count -eq 1) { Target shell kill -INT $traceOwners[0] | Out-Null }
            if (!$traceProcess.WaitForExit(30000)) { throw 'Perfetto did not finish flushing the session trace.' }
        }
        if ($traceProcess.ExitCode -ne 0) { throw 'Perfetto failed to finish recording.' }
        Confirm-Profile
        for ($retry=0; ; $retry++) {
            try { Target pull $remote $path | Out-Null; break }
            catch { if ($retry -ge 2) { throw }; Start-Sleep -Seconds 1 }
        }
        Target shell rm -- $remote | Out-Null
        Target shell test '!' -e $remote | Out-Null
        $script:captureClean = $true
    }
    $integrity = @(& $TraceProcessor -Q "SELECT name,value FROM stats WHERE value>0 AND (severity='data_loss' OR name IN ('traced_buf_chunks_overwritten','traced_buf_trace_writer_packet_loss'))" $path 2> (Join-Path $Evidence "$name-integrity.log"))
    if ($LASTEXITCODE) { throw 'Trace integrity check could not run.' }
    $lost = @($integrity | ConvertFrom-Csv)
    if ($lost.Count) {
        $integrity | Set-Content -LiteralPath (Join-Path $Evidence "$name-data-loss.csv")
        $executionFailure ??= 'Perfetto reported data loss.'
    }
    if ($name -eq 'preflight-failure') { throw $executionFailure }
    if ($ExternalCold) {
        $coldSql = Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/t55-performance/handset-external-cold.sql'
        $coldRows = @(& $TraceProcessor -q $coldSql $path 2> (Join-Path $Evidence 'external-cold-query.log'))
        if ($LASTEXITCODE) { throw 'External cold frame trace query failed.' }
        $coldRows | Set-Content -LiteralPath (Join-Path $Evidence 'external-cold-frames.csv')
        $coldFrames = @($coldRows | ConvertFrom-Csv)
        $coldQualified = @($coldFrames | Where-Object usable_home_ms -cmatch '^\d+(?:\.\d+)?$').Count
        Write-Host "External cold: $coldQualified usable Home presentations qualified."
        if ($executionFailure) { throw $executionFailure }
        $firstPresentations = @($coldFrames | Where-Object first_presentation_ms -cmatch '^\d+(?:\.\d+)?$').Count
        if ($coldFrames.Count -ne 5 -or ($EmulatorPreflight -and ($coldQualified -lt 1 -or $firstPresentations -ne 5)) -or
            (!$EmulatorPreflight -and $coldQualified -ne 5)) {
            throw 'Incomplete external cold presentation results.'
        }
        return
    }
    $traceRows = @(& $TraceProcessor -q $sql $path 2> (Join-Path $Evidence "$name-trace-processor.log"))
    if ($LASTEXITCODE) { throw 'Displayed-frame qualification query failed.' }
    $traceRows | Set-Content -LiteralPath (Join-Path $Evidence "$name-traversals.csv")
    $frames = @($traceRows | ConvertFrom-Csv)
    $logPattern = '(HOME|RECORDS|PLAN|INSIGHTS),(trace-check|warmup|warm),(\d+),(COMMITTED|FAILED|TIMED_OUT),(\d+),(\d+),(\d+)\s*$'
    $observed = @{}
    foreach ($line in $timingStatus) {
        if ($line -match $logPattern) {
            $key = '{0}-{1}-{2}' -f $Matches[1],$Matches[2],$Matches[3]
            if ($observed.ContainsKey($key)) { $observed[$key] = $null }
            else { $observed[$key] = @($Matches[4],$Matches[5],$Matches[6],$Matches[7]) }
        }
    }
    $cohorts = if ($calibration) { @{'trace-check'=3} } else { [ordered]@{warmup=5;warm=30} }
    $qualified = 0
    $sha=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
    foreach ($cohort in $cohorts.Keys) {
        for ($i=1;$i -le $cohorts[$cohort];$i++) {
            $key="$route-$cohort-$i"
            $frame=@($frames | Where-Object sample -ceq "T55-$key")
            $event=$observed[$key]
            $outcome=if ($null -eq $event) {'UNRECORDED'} else {$event[0]}
            if ($null -eq $event) { $event = @($outcome,$null,$null,$null) }
            $render = $null
            if (!$lost.Count -and $outcome -eq 'COMMITTED' -and $frame.Count -eq 1 -and $frame[0].qualified_ms -cmatch '^\d+(?:\.\d+)?$') {
                $render=$frame[0].qualified_ms
                $outcome='QUALIFIED'
                $qualified++
            } elseif ($outcome -eq 'COMMITTED') {$outcome='UNQUALIFIED'}
            $rows.Add([pscustomobject]@{Route=$route;Cohort=$cohort;Attempt=$i;Outcome=$outcome;
                ActionNs=$event[1];ReadyNs=$event[2];SubmissionNs=$event[3];PresentationNs=$frame[0].display_end_ns;
                ActionToDisplayMs=$render;TraceSha256=$sha})
        }
    }
    $rows | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $Evidence 'handset-measurements.csv')
    Write-Host "$name completed; $qualified displayed frames qualified."
    if ($executionFailure) { throw $executionFailure }
    if (!$calibration) {
        if ($EmulatorPreflight) { return ($timingStatus.Count -eq 35 -and $observed.Count -eq 35 -and $qualified -gt 0) }
        return ($qualified -eq 35)
    }
    return ($qualified -eq 3 -and $timingStatus.Count -eq 3 -and $frames.Count -eq 3)
}
if ($EmulatorPreflight) {
    try { Capture-Cohort 'preflight-failure' 'HOME' $true }
    catch {
        if (!$script:captureClean -or $_.Exception.Message -ne 'Injected emulator capture-body failure.') { throw }
        Write-Host 'Injected capture failure recovered: trace flushed, retrieved and removed.'
    }
}
$incomplete = [Collections.Generic.List[string]]::new()
$routes = if ($EmulatorPreflight) { @('INSIGHTS') } else { @('HOME','INSIGHTS','PLAN') }
foreach ($route in $routes) {
    $name=$route.ToLowerInvariant()
    try {
        if (!(Capture-Cohort "$name-calibration" $route $true)) {
            throw 'Fewer than three rehearsal frames qualified. Full cohort not started.'
        }
        if (!(Capture-Cohort $name $route $false)) {
            throw 'One or more timed attempts did not qualify.'
        }
    } catch {
        if (!$script:captureClean) { throw }
        Confirm-Profile
        $incomplete.Add($route)
        Write-Warning "$route incomplete: $($_.Exception.Message)"
    }
}
try { Capture-Cohort 'external-cold' 'HOME' $false -ExternalCold }
catch {
    if (!$script:captureClean) { throw }
    $incomplete.Add('external-cold')
    Write-Warning $_.Exception.Message
}
if ($incomplete.Count) { throw ('Incomplete displayed cohorts: ' + ($incomplete -join ', ')) }
