package lk.finora.api.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import lk.finora.api.advice.ai.GeminiClient;
import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.verify.AdviceVerifier;

/** Explicit D44 smoke run. The IT suffix excludes this class from ordinary Surefire runs. */
class LiveProviderAcceptanceIT {
    // One candidate per adapter, each with at most one transport retry.
    private static final int MAX_HTTP_ATTEMPTS = 4;
    private record Candidate(LlmClient client, String model) {}

    @Test
    void bothProvidersReturnVerifiableSyntheticAdvice() {
        if (!"true".equals(System.getenv("FINORA_D44_APPROVED"))) {
            throw new IllegalStateException("Explicit D44 authorization is required");
        }
        int approvedLimit = Integer.parseInt(required("FINORA_D44_HTTP_LIMIT"));
        if (approvedLimit < MAX_HTTP_ATTEMPTS) {
            throw new IllegalStateException("Approved HTTP limit must cover both adapters and their retries");
        }
        if (!Boolean.getBoolean("jdk.httpclient.disableRetryConnect")
                || Boolean.getBoolean("jdk.httpclient.enableAllMethodRetry")) {
            throw new IllegalStateException("D44 requires connection retry disabled and POST replay disabled");
        }
        if (approvedLimit != MAX_HTTP_ATTEMPTS) throw new IllegalStateException("D44 smoke limit must be exactly four attempts");
        String groqModel = model("FINORA_LLM_GROQ_MODEL", "openai/gpt-oss-120b");
        String geminiModel = model("FINORA_LLM_GEMINI_MODEL", "gemini-3.8-flash");
        String groqKey = required("GROQ_API_KEY");
        String geminiKey = required("GEMINI_API_KEY");
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var clients = List.of(new Candidate(new GroqClient(http, AdviceTestFixtures.JSON, groqKey, groqModel), groqModel),
                new Candidate(new GeminiClient(http, AdviceTestFixtures.JSON, geminiKey, geminiModel), geminiModel));
        var facts = AdviceTestFixtures.facts();
        var rules = AdviceTestFixtures.advice();
        var verifier = new AdviceVerifier();
        int verified = 0;
        int attempts = 0;
        for (Candidate candidate : clients) {
            LlmClient client = candidate.client();
            long start = System.nanoTime();
            var response = client.generate(facts, rules);
            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            attempts += response.httpAttempts();
            org.junit.jupiter.api.Assertions.assertTrue(attempts <= MAX_HTTP_ATTEMPTS);
            var verdict = response.success() ? verifier.verify(response.reply(), facts) : null;
            boolean accepted = verdict != null && verdict.verified();
            if (accepted) verified++;
            String outcome = verdict == null ? response.failure().name() : verdict.reason().name();
            System.out.printf("D44 provider=%s model=%s httpSendAttempts=%d adapter=%s verifier=%s %s latencyMs=%d outcome=%s%n",
                    client.provider(), candidate.model(), response.httpAttempts(), response.success() ? "REPLY" : response.failure(),
                    verdict == null ? "NOT_RUN" : outcome, verificationEvidence(verdict), latencyMs,
                    accepted ? "VERIFIED" : "RULE_FALLBACK_REQUIRED");
        }
        System.out.printf("D44 verifiedCandidates=%d attemptedCandidates=%d httpSendAttempts=%d maxHttpAttempts=%d%n",
                verified, clients.size(), attempts, MAX_HTTP_ATTEMPTS);
        assertEquals(clients.size(), verified, "Both live provider candidates must verify in this smoke run");
    }

    static String verificationEvidence(AdviceVerifier.Verdict verdict) {
        if (verdict == null) return "schema=NOT_RUN claim=NOT_RUN numerical=NOT_RUN";
        return switch (verdict.reason()) {
            case ACCEPTED -> "schema=PASS claim=PASS numerical=PASS";
            case MALFORMED_JSON, INVALID_SHAPE -> "schema=FAIL claim=UNESTABLISHED numerical=UNESTABLISHED";
            case UNKNOWN_ACTION, DUPLICATE_ACTION, UNSUPPORTED_CLAIM, MISSING_SECTION, UNSAFE_TEXT ->
                    "schema=UNESTABLISHED claim=FAIL numerical=UNESTABLISHED";
            case FACT_MISMATCH, WRONG_UNIT_OR_PERIOD, TEXT_NUMBER_MISMATCH ->
                    "schema=UNESTABLISHED claim=UNESTABLISHED numerical=FAIL";
            case PROVIDER_FAILURE -> "schema=NOT_RUN claim=NOT_RUN numerical=NOT_RUN";
        };
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    private static String model(String name, String candidate) {
        String value = required(name);
        if (!candidate.equals(value)) throw new IllegalStateException("Unexpected candidate in " + name);
        return value;
    }
}
