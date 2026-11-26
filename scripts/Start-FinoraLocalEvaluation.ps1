[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Verify', 'Connected', 'Demo')][string]$Mode,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$MySqlBin,
    [string]$AndroidSdk,
    [string]$MavenUserHome,
    [ValidateRange(1024, 65535)][int]$DbPort = 3330,
    [ValidateRange(1024, 65535)][int]$AdbPort = 5039,
    [ValidateRange(5554, 5682)][int]$EmulatorPort = 5582
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ge 7) { $PSNativeCommandUseErrorActionPreference = $false }
$projectRoot = Split-Path $PSScriptRoot -Parent
$java = Join-Path $JavaHome 'bin\java.exe'
$mysql = Join-Path $MySqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $MySqlBin 'mysqladmin.exe'
$mysqlServer = Join-Path $MySqlBin 'mysqld.exe'
foreach ($tool in $java, $mysql, $mysqlAdmin, $mysqlServer) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing tool: $tool" }
}
$ports = @($DbPort)
if ($Mode -ne 'Verify') {
    if (-not $AndroidSdk -or $EmulatorPort % 2) { throw 'Supply the SDK and an even emulator port.' }
    $ports += @(8080, $AdbPort, $EmulatorPort, ($EmulatorPort + 1))
}
if (($ports | Select-Object -Unique).Count -ne $ports.Count -or
        (Get-NetTCPConnection -State Listen -LocalPort $ports -ErrorAction SilentlyContinue)) {
    throw 'A required local port is occupied or repeated.'
}
$session = Join-Path ([IO.Path]::GetTempPath()) ('finora-local-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($session)
Write-Host "Session evidence: $session"
$data = Join-Path $session 'mysql'
$connectionOverrides = @(Get-ChildItem Env: | Where-Object {
    $_.Name -like 'SPRING_DATASOURCE_*' -or $_.Name -like 'SPRING_FLYWAY_*'
} | ForEach-Object { $_.Name })
$profileOverrides = @(Get-ChildItem Env: | Where-Object {
    $_.Name -like 'SPRING_PROFILES_*'
} | ForEach-Object { $_.Name })
$envNames = @(@('JAVA_HOME','Path','MAVEN_USER_HOME','MAVEN_OPTS','MAVEN_ARGS','MAVEN_SKIP_RC',
    'MAVEN_DEBUG_OPTS','JVM_CONFIG_MAVEN_OPTS','MAVEN_BASEDIR',
    'MYSQL_PWD','TEST_DB_URL','TEST_DB_USER','TEST_DB_PASS',
    'DB_URL','DB_USER','DB_PASS','JWT_SECRET','SPRING_PROFILES_ACTIVE','SERVER_ADDRESS','SERVER_PORT',
    'SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
    'SPRING_FLYWAY_URL','SPRING_FLYWAY_USER','SPRING_FLYWAY_PASSWORD',
    'ANDROID_HOME','ANDROID_ADB_SERVER_PORT','ADB_SERVER_PORT','FINORA_LLM_ENABLED','GROQ_API_KEY',
    'GEMINI_API_KEY','FINORA_LLM_GROQ_API_KEY','FINORA_LLM_GEMINI_API_KEY','FIREBASE_CREDENTIALS',
    'FINORA_FIREBASE_CREDENTIALS',
    'GOOGLE_APPLICATION_CREDENTIALS','SPRING_APPLICATION_JSON','SPRING_CONFIG_NAME','SPRING_CONFIG_LOCATION',
    'SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_IMPORT','JDK_JAVA_OPTIONS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS') +
    $connectionOverrides + $profileOverrides | Select-Object -Unique)
$previous = @{}
foreach ($name in $envNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$adbOwner = $null
$emulator = $null
$api = $null
$database = $null
$dbReady = $false
$dbSecured = $false
$mapped = $false
$permissionChanged = $false
$serial = "emulator-$EmulatorPort"
$avd = 'FINORA_T55_Evaluation'
$appId = 'lk.finora.app.acceptance'
$testId = "$appId.test"
$dbUser = 'finora_local'
$dbPass = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$rootPass = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')

function Invoke-Device {
    & $adb -P $AdbPort -s $serial @args
    if ($LASTEXITCODE) { throw 'Dedicated emulator command failed.' }
}

try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$(Join-Path $JavaHome 'bin');$env:SystemRoot\System32\WindowsPowerShell\v1.0;$env:Path"
    if ($MavenUserHome) { $env:MAVEN_USER_HOME = $MavenUserHome }
    foreach ($name in 'MYSQL_PWD','GROQ_API_KEY','GEMINI_API_KEY','FINORA_LLM_GROQ_API_KEY',
            'FINORA_LLM_GEMINI_API_KEY','FIREBASE_CREDENTIALS','FINORA_FIREBASE_CREDENTIALS','GOOGLE_APPLICATION_CREDENTIALS',
            'SPRING_APPLICATION_JSON','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION',
            'SPRING_CONFIG_IMPORT','JDK_JAVA_OPTIONS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS',
            'MAVEN_DEBUG_OPTS','JVM_CONFIG_MAVEN_OPTS','MAVEN_BASEDIR') {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
    # Direct Spring and pool-specific overrides outrank the DB_URL/TEST_DB_URL placeholders.
    foreach ($name in ($connectionOverrides + $profileOverrides)) {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
    $env:MAVEN_SKIP_RC = '1'
    $env:SPRING_CONFIG_NAME = 'application'
    $env:SPRING_PROFILES_ACTIVE = if ($Mode -eq 'Verify') { 'test' } else { 'dev' }
    $env:FINORA_LLM_ENABLED = 'false'
    $init = Start-Process $mysqlServer -ArgumentList @('--no-defaults','--initialize-insecure', "--datadir=`"$data`"") -Wait -PassThru -WindowStyle Hidden -RedirectStandardError (Join-Path $session 'mysql-init.log')
    if ($init.ExitCode) { throw 'Disposable MySQL initialization failed.' }
    $dbLog = Join-Path $session 'mysql.log'
    $database = Start-Process $mysqlServer -ArgumentList @('--no-defaults',"--datadir=`"$data`"","--port=$DbPort",
        '--bind-address=127.0.0.1','--mysqlx=OFF','--skip-named-pipe',"--log-error=`"$dbLog`"") -PassThru -WindowStyle Hidden
    for ($i = 0; $i -lt 30 -and -not $dbReady; $i++) {
        & $mysqlAdmin --no-defaults -h 127.0.0.1 -P $DbPort -u root ping *> $null
        $dbReady = $LASTEXITCODE -eq 0
        if (-not $dbReady) { Start-Sleep -Seconds 1 }
    }
    if (-not $dbReady) { throw 'Disposable MySQL did not become ready.' }
    $listener = @(Get-NetTCPConnection -State Listen -LocalPort $DbPort)
    if ($listener.Count -ne 1 -or $listener[0].LocalAddress -ne '127.0.0.1') { throw 'Unexpected MySQL binding.' }
    $dbProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0].OwningProcess)"
    if ($dbProcess.Name -ne 'mysqld.exe' -or $dbProcess.CommandLine -notlike "*$data*" -or
            $listener[0].OwningProcess -notin @($database.Id) -and $dbProcess.ParentProcessId -ne $database.Id) {
        throw 'Unexpected MySQL process.'
    }
    $sql = "CREATE DATABASE finora_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPass'; GRANT ALL ON finora_test.* TO '$dbUser'@'127.0.0.1'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPass';"
    $sql | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u root
    if ($LASTEXITCODE) { throw 'Disposable database initialization failed.' }
    $dbSecured = $true
    $env:MYSQL_PWD = $dbPass
    $identity = 'SELECT @@datadir, DATABASE(), CURRENT_USER()' | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u $dbUser -B -r -N finora_test
    if ($LASTEXITCODE -or (($identity -replace '\\','/') -notlike "$($data -replace '\\','/')*finora_test*$dbUser@127.0.0.1")) { throw 'Disposable database identity mismatch.' }
    Remove-Item Env:MYSQL_PWD
    $url = "jdbc:mysql://127.0.0.1:$DbPort/finora_test"
    # Both the application and Flyway must use the instance whose identity was just checked.
    # Credentials stay in this process environment; none are passed as command arguments.
    $env:SPRING_DATASOURCE_URL = $url
    $env:SPRING_DATASOURCE_USERNAME = $dbUser
    $env:SPRING_DATASOURCE_PASSWORD = $dbPass
    $env:SPRING_FLYWAY_URL = $url
    $env:SPRING_FLYWAY_USER = $dbUser
    $env:SPRING_FLYWAY_PASSWORD = $dbPass
    if ($Mode -eq 'Verify') {
        $env:TEST_DB_URL = $url
        $env:TEST_DB_USER = $dbUser
        $env:TEST_DB_PASS = $dbPass
        Push-Location (Join-Path $projectRoot 'api')
        try {
            $mavenLog = Join-Path $session 'maven-verify.log'
            & .\mvnw.cmd -o -B '-Dfinora.llm.enabled=false' '-Dfinora.firebase.credentials=' verify *> $mavenLog
            $mavenExit = $LASTEXITCODE
            Get-Content -LiteralPath $mavenLog -Tail 24
            if ($mavenExit) { throw 'Maven verification failed.' }
        } finally { Pop-Location }
        return
    }

    $adb = Join-Path $AndroidSdk 'platform-tools\adb.exe'
    $emulatorExe = Join-Path $AndroidSdk 'emulator\emulator.exe'
    $jar = Join-Path $projectRoot 'api\target\api-0.0.1-SNAPSHOT.jar'
    $app = Join-Path $projectRoot 'android\app\build\outputs\apk\v1\debug\app-v1-debug.apk'
    $test = Join-Path $projectRoot 'android\app\build\outputs\apk\androidTest\v1\debug\app-v1-debug-androidTest.apk'
    $aapt = Join-Path $AndroidSdk 'build-tools\36.0.0\aapt.exe'
    foreach ($file in @($adb,$emulatorExe,$jar,$app,$aapt) + $(if ($Mode -eq 'Connected') { @($test) })) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Build or install prerequisite: $file" }
    }
    if (-not ((& $aapt dump badging $app) -match "^package: name='$appId'")) { throw 'Expected the separate acceptance APK.' }
    if ($Mode -eq 'Connected' -and (-not ((& $aapt dump badging $test) -match "^package: name='$testId'") -or
            -not ((& $aapt dump xmltree $test AndroidManifest.xml) -match "android:targetPackage.*=`"$appId`""))) {
        throw 'Expected the separate acceptance instrumentation APK.'
    }
    if (Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'emulator|qemu' -and $_.CommandLine -like "*$avd*" }) {
        throw 'The dedicated FINORA AVD is already in use.'
    }
    $env:DB_URL = $url
    $env:DB_USER = $dbUser
    $env:DB_PASS = $dbPass
    $env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = '8080'
    $api = Start-Process $java -ArgumentList @('-jar',"`"$jar`"",'--finora.llm.enabled=false','--finora.firebase.credentials=') -WindowStyle Hidden -PassThru -WorkingDirectory $session -RedirectStandardOutput (Join-Path $session 'api.out') -RedirectStandardError (Join-Path $session 'api.err')
    $healthy = $false
    for ($i = 0; $i -lt 60 -and -not $healthy; $i++) {
        try { $healthy = (Invoke-RestMethod -TimeoutSec 2 -Uri 'http://127.0.0.1:8080/actuator/health').status -eq 'UP' } catch {}
        if ($api.HasExited) { throw 'Local FINORA API exited early.' }
        if (-not $healthy) { Start-Sleep -Seconds 1 }
    }
    $apiListener = Get-NetTCPConnection -State Listen -LocalPort 8080
    if (-not $healthy -or $apiListener.OwningProcess -ne $api.Id -or $apiListener.LocalAddress -ne '127.0.0.1') { throw 'Local API listener verification failed.' }
    $env:ANDROID_HOME = $AndroidSdk
    $env:ANDROID_ADB_SERVER_PORT = "$AdbPort"
    $env:ADB_SERVER_PORT = "$AdbPort"
    & $adb -P $AdbPort start-server
    if ($LASTEXITCODE) { throw 'Private ADB startup failed.' }
    $adbOwner = (Get-NetTCPConnection -State Listen -LocalPort $AdbPort).OwningProcess
    $emulatorArgs = @('-avd',$avd,'-port',"$EmulatorPort",'-read-only','-no-snapshot','-no-audio','-no-boot-anim')
    $window = 'Normal'
    if ($Mode -eq 'Connected') { $emulatorArgs += '-no-window'; $window = 'Hidden' }
    $emulator = Start-Process $emulatorExe -ArgumentList $emulatorArgs -WindowStyle $window -PassThru -RedirectStandardOutput (Join-Path $session 'emulator.out') -RedirectStandardError (Join-Path $session 'emulator.err')
    for ($i = 0; $i -lt 90; $i++) {
        $boot = (& $adb -P $AdbPort -s $serial shell getprop sys.boot_completed 2>$null) -join ''
        if ($boot.Trim() -eq '1') { break }
        if ($emulator.HasExited) { throw 'Dedicated emulator exited early.' }
        Start-Sleep -Seconds 2
    }
    if ($boot.Trim() -ne '1' -or (Invoke-Device emu avd name | Select-Object -First 1) -ne $avd -or
            (Invoke-Device shell getprop ro.hardware).Trim() -notin @('ranchu','goldfish') -or
            (Invoke-Device shell am get-current-user).Trim() -ne '0') { throw 'Dedicated emulator identity mismatch.' }
    if ((Invoke-Device reverse --list) -match 'tcp:8080') { throw 'Existing reverse mapping must be preserved.' }
    Invoke-Device reverse tcp:8080 tcp:8080
    $mapped = $true
    Invoke-Device install -r -t --user 0 $app
    if ($Mode -eq 'Demo') {
        Invoke-Device shell am start -n "$appId/lk.finora.app.ui.login.LoginActivity"
        [void](Read-Host 'Register only synthetic accounts in FINORA. Press Enter to stop the isolated session')
        return
    }
    Invoke-Device install -r -t --user 0 $test
    if (-not ((Invoke-Device shell pm list instrumentation $appId) -match [regex]::Escape("$testId/androidx.test.runner.AndroidJUnitRunner"))) {
        throw 'Unexpected installed instrumentation.'
    }
    $permission = (Invoke-Device shell dumpsys package $appId | Select-String 'android.permission.READ_SMS: granted=' | Select-Object -First 1).Line
    if ($permission -match 'granted=true') {
        Invoke-Device shell pm revoke --user 0 $appId android.permission.READ_SMS
        $permissionChanged = $true
    }
    $suite = 'lk.finora.app.acceptance.UserJourneyTest'
    $cases = "$suite#dailyGuideAndGoalSimulationFromPlanUseTheSignedInOwner,$suite#syntheticPasteFeedsInsightsAndPlanningSupportsSinhala"
    $output = Invoke-Device shell am instrument -w -r -e finoraIsolatedEmulator true -e class $cases "$testId/androidx.test.runner.AndroidJUnitRunner"
    $output | Set-Content -LiteralPath (Join-Path $session 'connected.log')
    $output | Select-String -Pattern '^(OK|FAILURES|Tests run|Time:|INSTRUMENTATION_STATUS: test=)'
    if (-not ($output -match '^OK \(2 tests\)')) { throw 'Connected verification failed. Inspect the session log.' }
} finally {
    try {
        if ($emulator -and -not $emulator.HasExited) {
            $ownedDevice = $false
            try {
                $reportedAvd = & $adb -P $AdbPort -s $serial emu avd name 2>$null | Select-Object -First 1
                $ownedDevice = $LASTEXITCODE -eq 0 -and $reportedAvd -eq $avd
                if ($ownedDevice) {
                    if ($permissionChanged) {
                        try {
                            Invoke-Device shell pm grant --user 0 $appId android.permission.READ_SMS
                            if (-not ((Invoke-Device shell dumpsys package $appId) -match 'android.permission.READ_SMS: granted=true')) {
                                Write-Warning 'Could not confirm acceptance app permission restoration.'
                            }
                        } catch { Write-Warning 'Acceptance app permission restoration failed.' }
                    }
                    if ($mapped) {
                        try {
                            Invoke-Device reverse --remove tcp:8080
                            if ((Invoke-Device reverse --list) -match 'tcp:8080') { Write-Warning 'Reverse mapping still present.' }
                        } catch { Write-Warning 'Session reverse mapping removal failed.' }
                    }
                }
            } catch { Write-Warning 'Could not verify the session emulator during cleanup.' }
            finally {
                if ($ownedDevice) {
                    try { Invoke-Device emu kill }
                    catch { Write-Warning 'Emulator console shutdown failed; stopping the session process.' }
                }
                if (-not $emulator.HasExited -and -not $emulator.WaitForExit(5000)) {
                    Stop-Process -InputObject $emulator -ErrorAction SilentlyContinue
                }
            }
        }
    } finally {
        try {
            if ($adbOwner -and (Get-NetTCPConnection -State Listen -LocalPort $AdbPort -ErrorAction SilentlyContinue).OwningProcess -eq $adbOwner) {
                & $adb -P $AdbPort kill-server
            }
            if ($api -and -not $api.HasExited) { Stop-Process -InputObject $api }
            if ($dbReady -and $dbProcess -and $dbProcess.CommandLine -like "*$data*") {
                if ($dbSecured) { $env:MYSQL_PWD = $rootPass }
                & $mysqlAdmin --no-defaults --protocol=TCP -h 127.0.0.1 -P $DbPort -u root shutdown
                if ($LASTEXITCODE) { Write-Warning 'Disposable MySQL shutdown could not be confirmed.' }
            } elseif ($database -and -not $database.HasExited) {
                Stop-Process -InputObject $database
            }
        } finally {
            foreach ($name in $envNames) {
                if ($null -eq $previous[$name]) {
                    Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
                } else {
                    [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
                }
            }
            Write-Host "Disposable data and evidence retained at $session"
        }
    }
}
