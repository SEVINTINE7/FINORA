package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.Test;

import lk.finora.app.data.repo.TransactionRepository.Filters;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

public class TransactionRepositoryTest {
    @Test public void createsManualRecordWithoutReplayingAfterDisconnect() throws IOException, InterruptedException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.enqueue(new MockResponse().setResponseCode(201));
            var draft = new TransactionRepository.Edit(1, null, "EXPENSE",
                    new BigDecimal("123.40"), "2026-11-23", null, null);
            var request = TransactionRepository.createRequest(server.url("/api/v1/transactions"), draft);
            assertTrue(request.body().isOneShot());
            assertThrows(IOException.class, () -> new OkHttpClient().newCall(request).execute().close());
            assertEquals(1, server.getRequestCount());
            var posted = server.takeRequest();
            assertEquals("POST", posted.getMethod());
            String json = posted.getBody().readUtf8();
            assertTrue(json.contains("\"source\":\"MANUAL\""));
            assertTrue(json.contains("\"amount\":123.40"));
        }
    }

    @Test public void smsBatchIsOneShotAndExplicitRetryReusesStructuredPayload() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.enqueue(new MockResponse().setBody(
                    "{\"items\":[{\"index\":1,\"status\":\"DUPLICATE\",\"possibleManualMatch\":[]}]}"));
            var transaction = new TransactionRepository.SmsTransaction(1, null, "EXPENSE",
                    new BigDecimal("1250.75"), "2026-11-21", "SYNTHETIC SHOP", "PASTE");
            var batch = java.util.List.of(new TransactionRepository.SmsBatchItem("a".repeat(64), transaction));
            var endpoint = server.url("/api/v1/transactions");
            var request = TransactionRepository.smsBatchRequest(endpoint, batch);
            var http = new OkHttpClient();
            assertTrue(request.body().isOneShot());
            assertThrows(IOException.class, () -> http.newCall(request).execute().close());
            assertEquals(1, server.getRequestCount());
            var first = server.takeRequest();
            assertEquals("/api/v1/transactions/batch", first.getPath());
            String posted = first.getBody().readUtf8();
            var item = com.google.gson.JsonParser.parseString(posted).getAsJsonObject()
                    .getAsJsonArray("items").get(0).getAsJsonObject();
            assertEquals(java.util.Set.of("dedupHash", "transaction"), item.keySet());
            assertEquals(java.util.Set.of("accountId", "type", "amount", "txnDate", "merchant", "source"),
                    item.getAsJsonObject("transaction").keySet());

            try (var response = http.newCall(TransactionRepository.smsBatchRequest(endpoint, batch)).execute()) {
                assertEquals("DUPLICATE", TransactionRepository.readSmsBatch(response, 1).items().get(0).status());
            }
            assertEquals(posted, server.takeRequest().getBody().readUtf8());
        }
    }

    @Test public void rejectsIncompleteBatchResponseAsUncertain() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"items\":[]}"));
            var request = new okhttp3.Request.Builder().url(server.url("/")).build();
            try (var response = new OkHttpClient().newCall(request).execute()) {
                assertThrows(IOException.class, () -> TransactionRepository.readSmsBatch(response, 1));
            }
        }
    }

    @Test public void includesAllSelectedFiltersAndZeroBasedPage() {
        HttpUrl endpoint = HttpUrl.get("https://example.test/api/v1/transactions");
        HttpUrl url = TransactionRepository.pageUrl(endpoint,
                new Filters("2026-11-01", "2026-11-30", "EXPENSE", 42L, 77L), 2);
        assertEquals("2026-11-01", url.queryParameter("from"));
        assertEquals("2026-11-30", url.queryParameter("to"));
        assertEquals("EXPENSE", url.queryParameter("type"));
        assertEquals("42", url.queryParameter("categoryId"));
        assertEquals("77", url.queryParameter("accountId"));
        assertEquals("2", url.queryParameter("page"));
    }

    @Test public void omitsUnsetFilters() {
        HttpUrl url = TransactionRepository.pageUrl(HttpUrl.get("https://example.test/api/v1/transactions"),
                new Filters(null, null, null, null, null), 0);
        assertEquals(1, url.querySize());
        assertNull(url.queryParameter("type"));
        assertEquals("0", url.queryParameter("page"));
    }
}
