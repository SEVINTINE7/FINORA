package lk.finora.api.advice.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One retry for 429/5xx/transport failure. Only bounded metadata is logged, never exceptions or payloads. */
abstract class JsonHttpLlmClient implements LlmClient {
    private static final Logger LOG = LoggerFactory.getLogger(JsonHttpLlmClient.class);
    protected static final String TRANSPORT_SYSTEM = AiPrompt.SYSTEM.replace(
            "Return only a JSON array matching the supplied response schema.",
            "Return only a JSON object with an advice array matching the supplied item schema.") + """
            Transport contract: return a JSON object with only an advice property containing the advice array.
            Copy eligible text and numeric provenance exactly; you may only select and order eligible actions.
            """;
    protected final JsonMapper mapper;
    protected final String model;
    private final HttpClient http;
    private final URI endpoint;
    private final String key;
    private final Duration timeout;

    JsonHttpLlmClient(HttpClient http, JsonMapper mapper, URI endpoint, String key, String model, Duration timeout) {
        this.http = http; this.mapper = mapper; this.endpoint = endpoint; this.key = key;
        this.model = model; this.timeout = timeout;
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(15)) > 0)
            throw new IllegalArgumentException("Provider timeout must be positive and at most 15 seconds");
        if (!"https".equals(endpoint.getScheme())) throw new IllegalArgumentException("Provider endpoint must use HTTPS");
    }
    protected abstract Map<String, Object> payload(String user);
    protected abstract String authHeader();
    protected abstract String authValue(String key);
    protected abstract String extract(JsonNode body);

    @Override
    public Result generate(FactSheet facts, List<AdviceItem> eligibleActions) {
        if (key == null || key.isBlank() || model == null || model.isBlank()) return Result.failed(Failure.UNCONFIGURED);
        String body = mapper.writeValueAsString(payload(AiPrompt.user(facts, eligibleActions, mapper)));
        for (int attempt = 0; attempt < 2; attempt++) {
            String requestId = UUID.randomUUID().toString();
            long start = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header(authHeader(), authValue(key))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status != 200) {
                    log(requestId, start, "HTTP_" + status, null, null);
                    if (attempt == 0 && (status == 429 || status >= 500 && status <= 599)) continue;
                    return Result.failed(Failure.HTTP_ERROR);
                }
                if (response.body() == null || response.body().length() > 64000) {
                    log(requestId, start, "INVALID_RESPONSE", null, null);
                    return Result.failed(Failure.INVALID_RESPONSE);
                }
                try {
                    JsonNode json = mapper.readTree(response.body());
                    String reply = extract(json);
                    if (reply == null || reply.isBlank() || reply.length() > 16000) throw new IllegalArgumentException();
                    log(requestId, start, "HTTP_200", tokens(json, "prompt_tokens", "promptTokenCount"),
                            tokens(json, "completion_tokens", "candidatesTokenCount"));
                    return Result.received(reply);
                } catch (RuntimeException invalid) {
                    log(requestId, start, "INVALID_RESPONSE", null, null);
                    return Result.failed(Failure.INVALID_RESPONSE);
                }
            } catch (HttpTimeoutException timeoutFailure) {
                log(requestId, start, "TIMEOUT", null, null);
                if (attempt == 1) return Result.failed(Failure.TIMEOUT);
            } catch (IOException transportFailure) {
                log(requestId, start, "IO_ERROR", null, null);
                if (attempt == 1) return Result.failed(Failure.IO_ERROR);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log(requestId, start, "INTERRUPTED", null, null);
                return Result.failed(Failure.INTERRUPTED);
            }
        }
        return Result.failed(Failure.IO_ERROR);
    }

    protected static String text(JsonNode node) {
        if (node == null || !node.isString()) throw new IllegalArgumentException("Invalid provider envelope");
        return node.asString();
    }
    private Long tokens(JsonNode body, String groqField, String geminiField) {
        JsonNode value = body.path("usage").path(groqField);
        if (!value.isIntegralNumber()) value = body.path("usageMetadata").path(geminiField);
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null;
    }
    private void log(String id, long start, String status, Long input, Long output) {
        LOG.info("LLM requestId={} latencyMs={} status={} inputTokens={} outputTokens={}",
                id, (System.nanoTime() - start) / 1_000_000, status, input, output);
    }
}
