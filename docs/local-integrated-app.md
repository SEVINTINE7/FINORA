# Run the integrated FINORA app locally

Use the Windows checkout, Java 17, MySQL 8 binaries, Android Studio JBR, the Android SDK and a dedicated AVD named `FINORA_T55_Evaluation` with Android user 0. Install the checked-in wrapper distributions and dependencies through the normal project setup before using the offline commands. Close any existing FINORA evaluation session first.

From the repository root, use the actual local tool paths:

```powershell
$java17 = 'C:\path\to\jdk-17'
$mysql = 'C:\path\to\mysql-8.0\bin'
$sdk = 'C:\path\to\Android\Sdk'
.\scripts\Start-FinoraLocalEvaluation.ps1 -Mode Verify -JavaHome $java17 -MySqlBin $mysql

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = $sdk
Push-Location android
try {
    .\gradlew.bat --offline assembleV1Debug assembleV1DebugAndroidTest -PfinoraHandsetAcceptance=true '-PfinoraFcmAppId=' '-PfinoraFcmApiKey=' '-PfinoraFcmProjectId=' '-PfinoraFcmSenderId='
    if ($LASTEXITCODE) { throw 'Android build failed.' }
} finally { Pop-Location }

.\scripts\Start-FinoraLocalEvaluation.ps1 -Mode Connected -JavaHome $java17 -MySqlBin $mysql -AndroidSdk $sdk
```

Use `-Mode Demo` in the last command for a manual session. The emulator opens the FINORA sign-in screen. Register a disposable `@example.test` account in the app, complete consent, then sign in normally. For a planning walkthrough, use LKR 55,000 available, LKR 5,000 planned savings, LKR 3,000 of additional unpaid bills and a next income date ten days away. Confirm the quoted tracked bills. In an empty profile the daily result is LKR 4,700. Add a new savings goal with a target of LKR 120,000, then run Goal Simulation with LKR 25,000 baseline and an adjustment of −LKR 5,000. The estimate is six monthly contributions of LKR 20,000. Press Enter in the launcher terminal to finish the demo.

Each invocation creates a fresh disposable loopback MySQL instance with generated credentials and its own data directory. The launcher applies no changes to the benchmark and uses the acceptance application ID. Connected mode registers synthetic users, verifies the planning and import journeys, and temporarily denies SMS for that acceptance package. It restores the prior permission, removes its own ADB reverse mapping and stops its own processes. The launcher prints the log directory and retains the disposable database files and evidence. Providers and Firebase stay disabled.
