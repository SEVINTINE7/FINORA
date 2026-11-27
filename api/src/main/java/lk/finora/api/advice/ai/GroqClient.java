package lk.finora.api.advice.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GroqClient extends JsonHttpLlmClient {
    public GroqClient(HttpClient http, JsonMapper mapper, String key, String model) {
        super(http, mapper, URI.create("https://api.groq.com/openai/v1/chat/completions"), key, model, Duration.ofSeconds(15));
    }
    @Override public String provider() { return "GROQ"; }
    @Override protected String authHeader() { return "Authorization"; }
    @Override protected String authValue(String key) { return "Bearer " + key; }
    @Override protected Map<String, Object> payload(String user) {
        return payload(user, TRANSPORT_SYSTEM);
    }
    @Override protected Map<String, Object> payload(String user, String system) {
        return Map.of("model", model, "response_format", Map.of("type", "json_object"), "messages", List.of(
                Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
    }
    @Override protected String extract(JsonNode body) {
        JsonNode choice = body.path("choices").path(0);
        if (!"stop".equals(text(choice.get("finish_reason")))) throw new IllegalArgumentException();
        return text(choice.path("message").get("content"));
    }
}
