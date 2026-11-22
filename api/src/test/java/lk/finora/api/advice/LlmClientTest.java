package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import lk.finora.api.advice.ai.GeminiClient;
import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.LlmClient;

@ExtendWith(OutputCaptureExtension.class)
class LlmClientTest {
    private final HttpClient http = mock(HttpClient.class); // No socket or real provider transport is used.
    private GroqClient groq() { return new GroqClient(http, AdviceTestFixtures.JSON, "MOCK_KEY_DO_NOT_LOG", "mock-model"); }
    private HttpResponse<String> response(int status, String body) {
        return new Reply(status, body);
    }
    private record Reply(int statusCode, String body) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
        public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a,b) -> true); }
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        public java.net.URI uri() { return java.net.URI.create("https://mock.invalid/"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    private LlmClient.Result run(LlmClient client) { return client.generate(AdviceTestFixtures.facts(), AdviceTestFixtures.advice()); }

    @Test
    void groqUsesJsonModeTimeoutWhitelistAndMetadataOnlyLogging(CapturedOutput output) throws Exception {
        String reply = AdviceTestFixtures.reply();
        when(http.<String>send(any(), any())).thenReturn(response(200, AdviceTestFixtures.groqEnvelope(reply)));
        assertThat(run(groq()).reply()).isEqualTo(reply);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(15));
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer MOCK_KEY_DO_NOT_LOG");
        String body = body(request.getValue());
        assertThat(body).contains("json_object", "eligibleActions", "factKey", "Copy eligible text")
                .doesNotContain("accountId", "userId", "fullName", "MOCK_KEY_DO_NOT_LOG");
        String userPayload = AdviceTestFixtures.JSON.readTree(body).get("messages").get(1).get("content").asString();
        assertThat(userPayload).doesNotContain("merchant", "accountId", "userId", "fullName");
        assertThat(output).contains("inputTokens=10", "outputTokens=20", "status=HTTP_200")
                .doesNotContain("MOCK_KEY_DO_NOT_LOG", AdviceTestFixtures.advice().get(0).text(), "eligibleActions");
    }

    @Test
    void geminiUsesHeaderKeyAndJsonGenerationWithoutQueryCredentials() throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(200, AdviceTestFixtures.geminiEnvelope(AdviceTestFixtures.reply())));
        assertThat(run(new GeminiClient(http, AdviceTestFixtures.JSON, "MOCK_KEY", "mock-model")).success()).isTrue();
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().headers().firstValue("x-goog-api-key")).contains("MOCK_KEY");
        assertThat(request.getValue().uri().getQuery()).isNull();
        assertThat(body(request.getValue())).contains("responseMimeType", "application/json", "eligibleActions");
    }

    @Test
    void rateLimitRetriesOnceThenCanSucceed() throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(429, "ignored"), response(200, AdviceTestFixtures.groqEnvelope(AdviceTestFixtures.reply())));
        assertThat(run(groq()).success()).isTrue(); verify(http, times(2)).send(any(), any());
    }

    @ParameterizedTest @ValueSource(ints = {429, 500, 503})
    void retryableStatusStopsAfterTwoAttempts(int status) throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(status, "PRIVATE ERROR BODY"));
        assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.HTTP_ERROR);
        verify(http, times(2)).send(any(), any());
    }

    @ParameterizedTest @ValueSource(ints = {400, 401, 403})
    void permanentHttpErrorsAreNotRetried(int status, CapturedOutput output) throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(status, "PRIVATE ERROR BODY"));
        assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.HTTP_ERROR);
        verify(http).send(any(), any()); assertThat(output).doesNotContain("PRIVATE ERROR BODY", "MOCK_KEY_DO_NOT_LOG");
    }

    @Test
    void timeoutRetriesOnceWithoutLoggingExceptionPayload(CapturedOutput output) throws Exception {
        when(http.<String>send(any(), any())).thenThrow(new HttpTimeoutException("SECRET TIMEOUT DETAILS"));
        assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.TIMEOUT);
        verify(http, times(2)).send(any(), any()); assertThat(output).doesNotContain("SECRET TIMEOUT DETAILS");
    }

    @Test
    void ioFailureRetriesOnce() throws Exception {
        when(http.<String>send(any(), any())).thenThrow(new IOException("PRIVATE URL AND KEY"));
        assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.IO_ERROR);
        verify(http, times(2)).send(any(), any());
    }

    @Test
    void interruptionIsPreservedAndNotRetried() throws Exception {
        when(http.<String>send(any(), any())).thenThrow(new InterruptedException("stop"));
        try {
            assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue(); verify(http).send(any(), any());
        } finally { Thread.interrupted(); }
    }

    @ParameterizedTest @ValueSource(strings = {"not json", "{}", "null", "{\"choices\":[]}",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"PRIVATE TEXT\"}}]}"})
    void malformedOrTruncatedProviderEnvelopesFailWithoutRetry(String body, CapturedOutput output) throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(200, body));
        assertThat(run(groq()).failure()).isEqualTo(LlmClient.Failure.INVALID_RESPONSE);
        verify(http).send(any(), any()); assertThat(output).doesNotContain("PRIVATE TEXT");
    }

    @Test
    void blockedGeminiEnvelopeFailsSafely() throws Exception {
        when(http.<String>send(any(), any())).thenReturn(response(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        assertThat(run(new GeminiClient(http, AdviceTestFixtures.JSON, "mock", "mock-model")).failure())
                .isEqualTo(LlmClient.Failure.INVALID_RESPONSE);
    }

    @Test
    void unconfiguredProvidersNeverAttemptHttp() throws Exception {
        assertThat(run(new GroqClient(http, AdviceTestFixtures.JSON, "", "mock-model")).failure()).isEqualTo(LlmClient.Failure.UNCONFIGURED);
        assertThat(run(new GeminiClient(http, AdviceTestFixtures.JSON, "mock", "")).failure()).isEqualTo(LlmClient.Failure.UNCONFIGURED);
        verifyNoInteractions(http);
    }

    private String body(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<Void> finished = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) { byte[] data = new byte[b.remaining()]; b.get(data); bytes.writeBytes(data); }
            public void onError(Throwable e) { finished.completeExceptionally(e); }
            public void onComplete() { finished.complete(null); }
        });
        finished.get(1, TimeUnit.SECONDS);
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
