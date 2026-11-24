package lk.finora.app.ui.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class RemoteSmsFormatsTest {
    private static final Path BUNDLE = Path.of("src/main/assets/sms/bank_formats.json");
    private static final String EMPTY = "{\"schemaVersion\":1,\"templates\":[]}";

    @Test public void authenticatedGetActivatesApprovedSnapshotWithoutSendingSms() throws Exception {
        try (var server = new MockWebServer()) {
            var bundle = bundle();
            server.enqueue(json(Files.readString(BUNDLE)));
            var state = new State();
            var remote = RemoteSmsFormats.load(call(server), bundle, state);
            assertNotSame(bundle, remote);
            assertTrue(remote.hasSameTemplates(bundle));
            var request = server.takeRequest();
            assertEquals("/api/v1/sms-formats", request.getPath());
            assertEquals("GET", request.getMethod());
            assertEquals("Bearer test-only", request.getHeader("Authorization"));
            assertEquals(0, request.getBodySize());
        }
    }

    @Test public void offlineAndInvalidResponsesKeepBundledFallback() throws Exception {
        var bundle = bundle();
        var state = new State();
        try (var server = new MockWebServer()) {
            String approved = Files.readString(BUNDLE);
            for (String invalid : List.of("{" , " ".repeat(65537),
                    EMPTY.replace(":1", ":2"), approved.replace("NSB", "UNAPPROVED"),
                    approved.replace("nsb-pos-debit", "nsb-outward-ceft-debit"),
                    approved.replace(".OTC Outward CEFT.", ". ATM POS Transaction."))) {
                server.enqueue(json(invalid));
                assertSame(bundle, RemoteSmsFormats.load(call(server), bundle, state));
            }
            server.enqueue(new MockResponse().setResponseCode(503));
            assertSame(bundle, RemoteSmsFormats.load(call(server), bundle, state));
            var offline = call(server);
            server.shutdown();
            assertSame(bundle, RemoteSmsFormats.load(offline, bundle, state));
            assertFalse(state.disabled);
        }
    }

    @Test public void emptyDisablesOfflineFallbackUntilValidNonemptyResponse() throws Exception {
        try (var server = new MockWebServer()) {
            var bundle = bundle();
            var state = new State();
            server.enqueue(json(EMPTY));
            assertTrue(RemoteSmsFormats.load(call(server), bundle, state).approvedSenderIds().isEmpty());
            assertTrue(state.disabled);
            server.enqueue(new MockResponse().setResponseCode(503));
            assertTrue(RemoteSmsFormats.load(call(server), bundle, state).approvedSenderIds().isEmpty());
            server.enqueue(json(Files.readString(BUNDLE)));
            assertTrue(RemoteSmsFormats.load(call(server), bundle, state).hasSameTemplates(bundle));
            assertFalse(state.disabled);
        }
    }

    @Test public void replacedLoginCannotPublishRemoteStateOrReceiveFallback() throws Exception {
        try (var server = new MockWebServer()) {
            var bundle = bundle();
            server.enqueue(json(EMPTY));
            assertThrows(IOException.class, () -> RemoteSmsFormats.load(call(server), bundle, next -> {
                throw new IOException("Login replaced");
            }));
            server.enqueue(new MockResponse().setResponseCode(503));
            assertThrows(IOException.class, () -> RemoteSmsFormats.load(call(server), bundle, next -> {
                throw new IOException("Login replaced");
            }));
        }
    }

    private static SmsParser bundle() throws IOException {
        try (var stream = Files.newInputStream(BUNDLE)) {
            return SmsTemplateLoader.load(stream);
        }
    }

    private static Call call(MockWebServer server) {
        var api = new ApiClient(server.url("/").toString(), () -> "test-only");
        return api.newCall(new Request.Builder().url(server.url("/api/v1/sms-formats")).build());
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static final class State implements RemoteSmsFormats.Disablement {
        private boolean disabled;
        @Override public boolean resolve(Boolean update) {
            if (update != null) disabled = update;
            return disabled;
        }
    }
}
