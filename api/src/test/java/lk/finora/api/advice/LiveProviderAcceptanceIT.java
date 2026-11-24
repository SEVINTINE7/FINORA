package lk.finora.api.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import lk.finora.api.advice.ai.GeminiClient;
import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.verify.AdviceVerifier;

/** Explicit D44 smoke run. The IT suffix excludes this class from ordinary Surefire runs. */
class LiveProviderAcceptanceIT {
    // One candidate per adapter, each with at most one transport retry.
    private static final int MAX_HTTP_ATTEMPTS = 4;
    private static final Pattern LOG_SAFE_MODEL = Pattern.compile("[A-Za-z0-9._/-]{1,100}");

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
        String groqModel = model("FINORA_LLM_GROQ_MODEL");
        String geminiModel = model("FINORA_LLM_GEMINI_MODEL");
        String groqKey = required("GROQ_API_KEY");
        String geminiKey = required("GEMINI_API_KEY");
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        List<LlmClient> clients = List.of(new GroqClient(http, AdviceTestFixtures.JSON, groqKey, groqModel),
                new GeminiClient(http, AdviceTestFixtures.JSON, geminiKey, geminiModel));
        var facts = AdviceTestFixtures.facts();
        var rules = AdviceTestFixtures.advice();
        var verifier = new AdviceVerifier();
        int verified = 0;
        for (LlmClient client : clients) {
            long start = System.nanoTime();
            var response = client.generate(facts, rules);
            var verdict = response.success() ? verifier.verify(response.reply(), facts) : null;
            boolean accepted = verdict != null && verdict.verified();
            if (accepted) verified++;
            String outcome = verdict == null ? response.failure().name() : verdict.reason().name();
            System.out.printf("D44 provider=%s outcome=%s latencyMs=%d delivered=%s%n", client.provider(),
                    outcome, Duration.ofNanos(System.nanoTime() - start).toMillis(), accepted ? "AI" : "RULE");
        }
        System.out.printf("D44 verifiedCandidates=%d attemptedCandidates=%d maxHttpAttempts=%d%n",
                verified, clients.size(), MAX_HTTP_ATTEMPTS);
        assertEquals(clients.size(), verified, "Both live provider candidates must verify in this smoke run");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }

    private static String model(String name) {
        String value = required(name);
        if (!LOG_SAFE_MODEL.matcher(value).matches()) throw new IllegalStateException("Invalid " + name);
        return value;
    }
}
