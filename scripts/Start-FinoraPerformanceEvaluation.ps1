param(
 [Parameter(Mandatory)][string]$RetainedEvaluation,
 [Parameter(Mandatory)][string]$ApiJar,
 [Parameter(Mandatory)][string]$ApiSha256,
 [Parameter(Mandatory)][string]$ApiRevision,
 [Parameter(Mandatory)][string]$Sdk,
 [Parameter(Mandatory)][string]$JavaHome,
 [Parameter(Mandatory)][string]$MySqlBin,
 [Parameter(Mandatory)][string]$Serial,
 [int]$DbPort=3360
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$source=(Resolve-Path -LiteralPath (Join-Path $RetainedEvaluation 'data')).Path
$jar=(Resolve-Path -LiteralPath $ApiJar).Path
$mysql=Join-Path $MySqlBin 'mysql.exe'
$frozenUuid='d236a029-e8bf-11f1-b816-00155de2fc9b'
$digest='14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944'
if ($DbPort -lt 1024 -or $DbPort -gt 65535 -or $DbPort -in 3317,8080 -or (Get-NetTCPConnection -State Listen -LocalPort 3317,$DbPort,8080 -ErrorAction SilentlyContinue)) {throw 'Evaluation ports are not available.'}
if (Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object CommandLine -like "*$source*") {throw 'Frozen MySQL is running.'}
$original=[IO.File]::ReadAllText((Join-Path $source 'auto.cnf'))
if ($original -notmatch [regex]::Escape($frozenUuid)) {throw 'Frozen server identity mismatch.'}
if ((Get-FileHash -LiteralPath $jar).Hash -ine $ApiSha256 -or $ApiRevision -cnotmatch '^[a-f0-9]{40}$') {throw 'API executable provenance mismatch.'}
git -C $repo diff --quiet $ApiRevision -- api/src/main api/pom.xml
if ($LASTEXITCODE) {throw 'API production source differs from executable provenance.'}
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
if ($Serial -cnotmatch '^[A-Za-z0-9]+$') { throw 'Invalid authorized handset serial.' }
$psFields=@(& $adb -s $Serial shell ps -p 1 -o 'PID,CMDLINE')
if ($LASTEXITCODE -or -not ($psFields -match '^\s*PID\s+CMDLINE\s*$')) {
    throw 'Handset does not support the exact Perfetto process-identity check.'
}
$keys=Import-Clixml -LiteralPath (Join-Path $RetainedEvaluation 'connection.xml')
foreach ($key in 'root','api','loader','worker') { if ($keys[$key] -isnot [SecureString]) {throw 'Protected evaluation credentials are unavailable.'} }
$session=Join-Path ([IO.Path]::GetTempPath()) ('finora-t55-redesign-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($session)
Write-Host "Performance evidence: $session"
$copy=Join-Path $session 'data'
& (Join-Path $env:SystemRoot 'System32/robocopy.exe') $source $copy /E /XJ /XF auto.cnf /R:0 /W:0 /NFL /NDL /NJH /NJS /NP > (Join-Path $session 'copy.log')
if($LASTEXITCODE -ge 8){throw 'Disposable evaluation copy failed.'}
function Secret($key) { [Net.NetworkCredential]::new('',$keys[$key]).Password }
function Listener($port,$expected) {
    $l=@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    if($l.Count -ne 1 -or $l[0].LocalAddress -ne '127.0.0.1'){throw "Unexpected listener on $port."}
    if($port -eq $DbPort){
        $proc=Get-CimInstance Win32_Process -Filter "ProcessId=$($l[0].OwningProcess)"
        if(!$proc -or $proc.ExecutablePath -ine (Join-Path $MySqlBin 'mysqld.exe') -or $proc.CommandLine -notlike "*$copy*" -or
           ($proc.ProcessId -ne $expected -and $proc.ParentProcessId -ne $expected)){throw 'Unexpected disposable MySQL listener owner.'}
    } elseif($l[0].OwningProcess -ne $expected){throw "Unexpected process on $port."}
}
function Query($sql) {
    $old=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=Secret 'api'
        $out=@($sql | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u finora_t55_api -N -B -r finora_t55_perf 2>$null)
        if($LASTEXITCODE){throw 'Disposable database verification query failed.'}
        $out
    } finally {$env:MYSQL_PWD=$old}
}
function Confirm-Data {
    $id=((Query 'SELECT @@server_uuid,@@port,@@bind_address,@@datadir,DATABASE(),CURRENT_USER()') -join '') -split '\t'
    if($id.Count -ne 6 -or $id[0] -ieq $frozenUuid -or $id[1] -ne "$DbPort" -or $id[2] -ne '127.0.0.1' -or
        (($id[3] -replace '\\','/').TrimEnd('/') -ine ($copy -replace '\\','/')) -or $id[4] -ne 'finora_t55_perf' -or $id[5] -ne 'finora_t55_api@127.0.0.1'){throw 'Disposable database identity mismatch.'}
    $counts=[ordered]@{accounts=2;transactions=826;budgets=24;bills=2;bill_payments=18;goals=2;goal_contributions=18;score_inputs=21}
    $owners="SELECT id FROM users WHERE email IN ('finora-t55-perf-worker@example.invalid','finora-t55-perf-irregular@example.invalid')"
    if((Query "SELECT COUNT(*) FROM ($owners) expected") -ne '2' -or
        (Query "SELECT COUNT(*) FROM users WHERE email NOT LIKE '%@example.invalid' AND email NOT LIKE '%@example.test'") -ne '0'){
        throw 'Unexpected benchmark identities.'
    }
    foreach($table in $counts.Keys){
        $filter=switch($table){
            'bill_payments' {"bill_id IN (SELECT id FROM bills WHERE user_id IN ($owners))"}
            'goal_contributions' {"goal_id IN (SELECT id FROM goals WHERE user_id IN ($owners))"}
            default {"user_id IN ($owners)"}
        }
        $count=Query "SELECT COUNT(*) FROM $table WHERE $filter"
        if($count -ne "$($counts[$table])"){throw "Canonical owner dataset count differs: $table ($count)."}
    }
    $rows=@(Query "SELECT CONCAT(CASE u.email WHEN 'finora-t55-perf-worker@example.invalid' THEN 'worker' ELSE 'irregular' END,'|',DATE_FORMAT(t.txn_date,'%Y-%m-%d'),'|',t.type,'|',COALESCE(c.name,'NULL'),'|',CAST(t.amount AS DECIMAL(20,2))) FROM transactions t JOIN users u ON u.id=t.user_id LEFT JOIN categories c ON c.id=t.category_id WHERE u.email IN ('finora-t55-perf-worker@example.invalid','finora-t55-perf-irregular@example.invalid') ORDER BY CASE u.email WHEN 'finora-t55-perf-worker@example.invalid' THEN 0 ELSE 1 END,t.id")
    $lf=[string][char]10
    $bytes=[Text.Encoding]::UTF8.GetBytes('FINORA-T55-v1'+$lf+($rows -join $lf)+$lf)
    $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if($rows.Count -ne 826 -or $hash -ine $digest){throw 'Canonical transaction digest differs.'}
    Write-Host "Verified disposable copy UUID $($id[0]), eight counts and canonical digest $digest."
}
$db=$api=$null
try {
    $db=Start-Process (Join-Path $MySqlBin 'mysqld.exe') -ArgumentList @('--no-defaults',("--datadir="+[char]34+$copy+[char]34),"--port=$DbPort",
        '--bind-address=127.0.0.1','--mysqlx=OFF','--skip-named-pipe',("--log-error="+[char]34+(Join-Path $session 'mysql.log')+[char]34)) -PassThru -WindowStyle Hidden
    for($i=0;$i -lt 35 -and !(Get-NetTCPConnection -State Listen -LocalPort $DbPort -ErrorAction SilentlyContinue);$i++){if($db.HasExited){throw 'Disposable MySQL exited.'};Start-Sleep 1}
    Listener $DbPort $db.Id
    Confirm-Data
    $migrationSecret = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $adminSql="CREATE USER 'finora_t55_migrate'@'127.0.0.1' IDENTIFIED BY '$migrationSecret'; GRANT SELECT,INSERT,UPDATE,CREATE,ALTER,INDEX,REFERENCES,LOCK TABLES ON finora_t55_perf.* TO 'finora_t55_migrate'@'127.0.0.1';"
    $oldPassword=$env:MYSQL_PWD
    try {
        $env:MYSQL_PWD=Secret 'root'
        $adminSql | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u root 2>$null | Out-Null
        if($LASTEXITCODE){throw 'Could not create the disposable schema-scoped migration account.'}
    } finally {$env:MYSQL_PWD=$oldPassword; $adminSql=$null}
    $url="jdbc:mysql://127.0.0.1:$DbPort/finora_t55_perf"
    $jwt=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
    $old=@{}
    foreach($name in @(Get-ChildItem Env: | Where-Object Name -match '^(SPRING_|FINORA_|DB_|JWT_|GROQ_|GEMINI_|FIREBASE_|GOOGLE_APPLICATION_CREDENTIALS$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)' | ForEach-Object Name)){
        $old[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
        [Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    try {
        $env:DB_URL=$url
        $env:DB_USER='finora_t55_api'
        $env:DB_PASS=Secret 'api'
        $env:JWT_SECRET=$jwt
        $env:SPRING_APPLICATION_JSON=@{
            'spring.config.name'='application';'spring.profiles.active'='dev';'server.address'='127.0.0.1';'server.port'=8080
            'spring.flyway.url'=$url;'spring.flyway.user'='finora_t55_migrate';'spring.flyway.password'=$migrationSecret
            'spring.jpa.hibernate.ddl-auto'='validate';'finora.llm.enabled'=$false;'finora.firebase.credentials'=''
            'management.health.mail.enabled'=$false
        }|ConvertTo-Json -Compress
        $api=Start-Process (Join-Path $JavaHome 'bin/java.exe') -ArgumentList '-jar',([char]34+$jar+[char]34) -WorkingDirectory $session -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $session 'api.log') -RedirectStandardError (Join-Path $session 'api.error.log')
    } finally {
        foreach($name in 'SPRING_APPLICATION_JSON','DB_URL','DB_USER','DB_PASS','JWT_SECRET'){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
        foreach($name in $old.Keys){[Environment]::SetEnvironmentVariable($name,$old[$name],'Process')}
    }
    $healthy=$false
    for($i=0;$i -lt 80 -and !$healthy;$i++){
        if($api.HasExited){throw 'Disposable API exited during startup.'}
        if(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue){
            Listener 8080 $api.Id
            try{$healthy=(Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2).status -ceq 'UP'}catch{}
        }
        if(!$healthy){Start-Sleep 1}
    }
    if(!$healthy){throw 'Disposable API did not become healthy.'}
    Confirm-Data
    $versions=@(Query 'SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank')
    if(!$versions.Count -or $versions[-1] -ne '25'){throw 'Disposable schema did not reach candidate V25.'}
    $app=Join-Path $repo 'android/app/build/outputs/apk/v1/debug/app-v1-debug.apk'
    $test=Join-Path $repo 'android/app/build/outputs/apk/androidTest/v1/debug/app-v1-debug-androidTest.apk'
    & (Join-Path $PSScriptRoot 'Invoke-FinoraHandsetJourneys.ps1') -Sdk $Sdk -Serial $Serial -AppApk $app -TestApk $test -Evidence $session -ApiPid $api.Id -PerformanceWorkerPassword $keys['worker'] -TraceProcessor (Join-Path $RetainedEvaluation 'execution41-trace-processor.exe')
} finally {
    if($api -and !$api.HasExited){Stop-Process -InputObject $api}
    if($db -and !$db.HasExited){
        Listener $DbPort $db.Id
        try {Confirm-Data} finally {
            $old=$env:MYSQL_PWD
            try {
                $env:MYSQL_PWD=Secret 'root'
                & (Join-Path $MySqlBin 'mysqladmin.exe') --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u root shutdown 2>$null | Out-Null
                if($LASTEXITCODE -or !$db.WaitForExit(12000)){throw 'Disposable database did not stop.'}
            } finally {$env:MYSQL_PWD=$old}
        }
    }
    if([IO.File]::ReadAllText((Join-Path $source 'auto.cnf')) -cne $original){throw 'Frozen identity changed.'}
    if(Get-NetTCPConnection -State Listen -LocalPort $DbPort,8080 -ErrorAction SilentlyContinue){throw 'Session listener remains.'}
    Write-Host "Session listeners closed. Evidence: $session"
}
