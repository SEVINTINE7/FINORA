package lk.finora.api.advice.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GeminiClient extends JsonHttpLlmClient {
    public GeminiClient(HttpClient http, JsonMapper mapper, String key, String model) {
        super(http, mapper, endpoint(model), key, model, Duration.ofSeconds(15));
    }
    private static URI endpoint(String model) {
        if (model != null && !model.isBlank() && !model.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("Invalid model id");
        return URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + (model == null || model.isBlank() ? "unconfigured" : model) + ":generateContent");
    }
    @Override public String provider() { return "GEMINI"; }
    @Override protected String authHeader() { return "x-goog-api-key"; }
    @Override protected String authValue(String key) { return key; }
    @Override protected Map<String, Object> payload(String user) {
        return payload(user, TRANSPORT_SYSTEM);
    }
    @Override protected Map<String, Object> payload(String user, String system) {
        return Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                "generationConfig", Map.of("responseMimeType", "application/json"));
    }
    @Override protected String extract(JsonNode body) {
        JsonNode candidate = body.path("candidates").path(0);
        if (!"STOP".equals(text(candidate.get("finishReason")))) throw new IllegalArgumentException();
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.size() != 1) throw new IllegalArgumentException();
        return text(parts.path(0).get("text"));
    }
}
