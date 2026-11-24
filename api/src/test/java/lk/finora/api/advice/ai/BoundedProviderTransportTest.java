package lk.finora.api.advice.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BoundedProviderTransportTest {
    @Test void exactLimitAndChunkedBodyAreAccepted() throws Exception {
        check(200, padded(64000), true, false, false, null, 1);
    }

    @Test void oversizedChunkedSuccessAndErrorAreRejectedWithoutRetry() throws Exception {
        check(200, padded(64001), true, false, false, LlmClient.Failure.INVALID_RESPONSE, 1);
        check(503, padded(64001), true, false, false, LlmClient.Failure.INVALID_RESPONSE, 1);
    }

    @Test void malformedAndEncodedBodiesAreRejected() throws Exception {
        check(200, "broken", false, false, false, LlmClient.Failure.INVALID_RESPONSE, 1);
        check(200, padded(40), false, false, true, LlmClient.Failure.INVALID_RESPONSE, 1);
    }

    @Test void stalledBodyTimesOutWithinTwoAttempts() throws Exception {
        check(200, padded(40), true, true, false, LlmClient.Failure.TIMEOUT, 2);
    }

    private static String padded(int size) {
        String prefix = "{\"reply\":\"[]\"}";
        return prefix + " ".repeat(size - prefix.length());
    }

    private void check(int status, String body, boolean chunked, boolean stall, boolean encoded,
            LlmClient.Failure failure, int attempts) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newFixedThreadPool(2);
        server.setExecutor(workers);
        AtomicInteger requests = new AtomicInteger();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                if (encoded) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
                exchange.getResponseBody().write(bytes, 0, 1);
                exchange.getResponseBody().flush();
                if (stall) Thread.sleep(5000);
                exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
            } catch (IOException closed) {
                // Expected when the subscriber cancels the response.
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            var mapper = JsonMapper.builder().build();
            var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var client = new JsonHttpLlmClient(HttpClient.newHttpClient(), mapper, endpoint, "synthetic", "fixture",
                    Duration.ofSeconds(stall ? 1 : 5)) {
                public String provider() { return "FIXTURE"; }
                protected String authHeader() { return "X-Fixture"; }
                protected String authValue(String key) { return key; }
                protected Map<String, Object> payload(String user) { return Map.of("fixture", user); }
                protected String extract(JsonNode json) { return text(json.get("reply")); }
            };
            var result = client.generate(new FactSheet("2026-11", Map.of(), List.of()), List.of());
            assertEquals(failure, result.failure());
            assertEquals(attempts, requests.get());
            assertEquals(attempts, result.httpAttempts());
            if (failure == null) assertEquals("[]", result.reply());
        } finally {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
