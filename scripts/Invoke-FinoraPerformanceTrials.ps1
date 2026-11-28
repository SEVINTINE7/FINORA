[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$AdbPath,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][ValidateRange(1,65535)][int]$UserId,
    [Parameter(Mandatory)][ValidatePattern('^[1-9][0-9]*$')][string]$UserSerial,
    [Parameter(Mandatory)][SecureString]$WorkerPassword,
    [Parameter(Mandatory)][string]$Evidence,
    [Parameter(Mandatory)][string]$TraceProcessor
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
    classification='HMD Crest; formal mid-range classification unapproved'
    cohort='Provisional, five warm-ups and 30 warm attempts per route, five instrumented fresh-process Home attempts'
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Evidence 'device.json')
$cfg = @"
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
buffers { size_kb: 8192 fill_policy: RING_BUFFER }
duration_ms: 300000
write_into_file: true
file_write_period_ms: 1000
max_file_size_bytes: 268435456
data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
  ftrace_events: "ftrace/print"
  ftrace_events: "sched/sched_process_fork"
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
function Capture-Cohort([string]$name, [string]$route, [bool]$cold) {
    Confirm-Profile
    $remote = "/data/misc/perfetto-traces/$prefix-$name.perfetto-trace"
    $path = Join-Path $Evidence "$name.perfetto-trace"
    $configuration = Join-Path $Evidence "$name-perfetto.pbtxt"
    $cfg | Set-Content -LiteralPath $configuration -Encoding utf8NoBOM
    $traceProcess = Start-Process $AdbPath -ArgumentList '-s',$Serial,'shell','-T','perfetto','--txt','-c','-','-o',$remote -PassThru -WindowStyle Hidden -RedirectStandardInput $configuration -RedirectStandardOutput (Join-Path $Evidence "$name-perfetto.log") -RedirectStandardError (Join-Path $Evidence "$name-perfetto-error.log")
    $timingStatus = [Collections.Generic.List[string]]::new()
    try {
        Start-Sleep -Seconds 3
        if ($traceProcess.HasExited) { throw 'Perfetto exited before the first measurement.' }
        $runs = if ($cold) { 1..5 } else { 1 }
        foreach ($n in $runs) {
            Confirm-Profile
            $invocation = @('shell','am','instrument','--user',"$UserId",'-w','-r','-e','class',$suite,
                '-e','finoraPerformancePilot','true','-e','finoraPerformanceHandset','true',
                '-e','finoraBuildFingerprint',$fingerprint,'-e','finoraTestUserSerial',$UserSerial)
            if ($cold) {
                Target shell am force-stop --user $UserId $app | Out-Null
                $invocation += @('-e','finoraFreshIndex',"$n")
            } else {
                Send-WorkerPassword
                $invocation += @('-e','finoraTraceRoute',$route)
            }
            $testResult = @(& $AdbPath -s $Serial @invocation $runner)
            $testResult | Set-Content -LiteralPath (Join-Path $Evidence "$name-test-$n.log")
            foreach ($status in ($testResult -cmatch '^INSTRUMENTATION_STATUS: finoraTiming=')) { $timingStatus.Add($status) }
        }
    } finally {
        if (!$traceProcess.HasExited) {
            $needle = '^\s*([1-9][0-9]*)\s+.*\bperfetto\b.*' + [regex]::Escape($remote) + '\s*$'
            $traceOwners = @(Target shell ps -A -o PID,ARGS | ForEach-Object { if ($_ -cmatch $needle) { $Matches[1] } })
            if ($traceOwners.Count -eq 1) { Target shell kill -INT $traceOwners[0] | Out-Null }
            if (!$traceProcess.WaitForExit(30000)) { throw 'Perfetto did not finish flushing the session trace.' }
        }
        if ($traceProcess.ExitCode -ne 0) { throw 'Perfetto failed to finish recording.' }
        Confirm-Profile
        Target pull $remote $path | Out-Null
        Target shell rm -- $remote | Out-Null
    }
    $traceRows = @(& $TraceProcessor -q $sql $path 2> (Join-Path $Evidence "$name-trace-processor.log"))
    if ($LASTEXITCODE) { throw 'Displayed-frame qualification query failed.' }
    $traceRows | Set-Content -LiteralPath (Join-Path $Evidence "$name-traversals.csv")
    $frames = @($traceRows | ConvertFrom-Csv)
    $logPattern = '(HOME|RECORDS|PLAN|INSIGHTS),(warmup|warm|fresh-process-launch),(\d+),(COMMITTED|FAILED|TIMED_OUT),(\d+),(\d+),(\d+)\s*$'
    $observed = @{}
    foreach ($line in $timingStatus) {
        if ($line -match $logPattern) {
            $key = '{0}-{1}-{2}' -f $Matches[1],$Matches[2],$Matches[3]
            if ($observed.ContainsKey($key)) { $observed[$key] = $null }
            else { $observed[$key] = @($Matches[4],$Matches[5],$Matches[6],$Matches[7]) }
        }
    }
    $cohorts = if ($cold) { @{'fresh-process-launch'=5} } else { [ordered]@{warmup=5;warm=30} }
    $sha=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
    foreach ($cohort in $cohorts.Keys) {
        for ($i=1;$i -le $cohorts[$cohort];$i++) {
            $key="$route-$cohort-$i"
            $frame=@($frames | Where-Object sample -ceq "T55-$key")
            $event=$observed[$key]
            $outcome=if ($null -eq $event) {'UNRECORDED'} else {$event[0]}
            if ($null -eq $event) { $event = @($outcome,$null,$null,$null) }
            $render = $null
            $coldStart = $null
            if ($outcome -eq 'COMMITTED' -and $frame.Count -eq 1 -and $frame[0].qualified_ms -cmatch '^\d+(?:\.\d+)?$') {
                if ($cold -and $frame[0].process_to_display_ms -cmatch '^\d+(?:\.\d+)?$') { $coldStart = $frame[0].process_to_display_ms }
                $render=$frame[0].qualified_ms
                $outcome=if (!$cold -or $coldStart) {'QUALIFIED'} else {'UNQUALIFIED'}
            } elseif ($outcome -eq 'COMMITTED') {$outcome='UNQUALIFIED'}
            $rows.Add([pscustomobject]@{Route=$route;Cohort=$cohort;Attempt=$i;Outcome=$outcome;
                ActionNs=$event[1];ReadyNs=$event[2];SubmissionNs=$event[3];PresentationNs=$frame[0].display_end_ns;
                ActionToDisplayMs=$render;ProcessToDisplayMs=$coldStart;TraceSha256=$sha})
        }
    }
    $rows | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $Evidence 'handset-measurements.csv')
    Write-Host "$name completed; $($rows.Count) attempts recorded."
}
foreach ($route in 'HOME','RECORDS','PLAN','INSIGHTS') { Capture-Cohort ($route.ToLowerInvariant()) $route $false }
Capture-Cohort 'cold-home' 'HOME' $true
