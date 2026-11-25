# D44 owner runbook

The candidates are `openai/gpt-oss-120b` and `gemini-3.8-flash`. Groq documents JSON Object Mode for the first. Google lists the second and a free tier. The Gemini adapter uses GenerateContent with `responseMimeType: application/json`. Check model availability for each exact account, endpoint and region before approval. Documentation alone cannot verify account eligibility, candidate envelope compatibility or zero expenditure.

- [Groq model card](https://console.groq.com/docs/model/openai/gpt-oss-120b)
- [Gemini pricing and free tier](https://ai.google.dev/gemini-api/docs/pricing)

Require recorded Thilina approval, appropriate team review, working reviewed transport, free-only credentials with no paid fallback, and four total attempts at zero expenditure. Set credentials from the authorized secret store in a fresh local Java 17 console. Never enter key values in commands, commit them or enable shell transcription or HTTP wire logging. The example uses masked input. From `api` after loading the established Java/Maven environment:

```powershell
$env:FINORA_LLM_GROQ_MODEL = 'openai/gpt-oss-120b'
$env:FINORA_LLM_GEMINI_MODEL = 'gemini-3.8-flash'
$env:FINORA_D44_HTTP_LIMIT = '4'
$env:FINORA_D44_APPROVED = 'true' # Only after approval is recorded
try {
    $env:GROQ_API_KEY = [Net.NetworkCredential]::new('', (Read-Host 'Groq credential' -AsSecureString)).Password
    $env:GEMINI_API_KEY = [Net.NetworkCredential]::new('', (Read-Host 'Gemini credential' -AsSecureString)).Password
    .\mvnw.cmd -B -o "-Dmaven.repo.local=$env:MAVEN_USER_HOME\repository" `
      '-Djdk.httpclient.disableRetryConnect=true' '-Djdk.httpclient.enableAllMethodRetry=false' `
      '-Dtest=LiveProviderAcceptanceIT' test
    $smokeExit = $LASTEXITCODE
} finally {
    Remove-Item Env:GROQ_API_KEY,Env:GEMINI_API_KEY,Env:FINORA_D44_APPROVED,Env:FINORA_D44_HTTP_LIMIT,Env:FINORA_LLM_GROQ_MODEL,Env:FINORA_LLM_GEMINI_MODEL -ErrorAction SilentlyContinue
}
if ($smokeExit -ne 0) { throw "D44 smoke failed with exit $smokeExit" }
```

Run the snippet from the reviewed checkout in one authorized PowerShell 5.1 process. Maven and Surefire inherit its process environment. Do not run again automatically. Verify the Groq organization is on a free plan with access to this exact model. Confirm the previously verified Gemini free-tier project is the one associated with the key and has access to the exact model on GenerateContent. Do not use a proxy that can replay requests. Obtain recorded approval of both models, four attempts including retries, and zero expenditure before setting the opt-in.

The harness makes one synthetic candidate call per adapter, each with at most one application retry. Redirects are disabled and the fresh test JVM disables connection retry and non-idempotent request replay. `httpSendAttempts` counts actual application sends, including attempts that fail before delivery. It is not provider billing or wire-level telemetry. Reports include model, adapter outcome, verifier reason, schema, claim and numerical stages, and elapsed submission time including retries. The verifier stops at the first rejection, so partial stages are conservatively shown as `UNESTABLISHED`. A rejected candidate requires rule fallback. The smoke harness does not exercise delivery, while mocked pipeline tests do. A successful two-candidate smoke run is availability and verification evidence only. T54 accuracy evaluation requires its own authorization and study design.
