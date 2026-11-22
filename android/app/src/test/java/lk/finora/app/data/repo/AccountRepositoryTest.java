package lk.finora.app.data.repo;

import java.math.BigDecimal;

import org.junit.Test;

import lk.finora.app.data.api.AccountApi;
import lk.finora.app.data.api.AccountDto;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AccountRepositoryTest {
    @Test
    public void createUsesExactDecimalAndOneShotBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201)
                    .setBody("{\"id\":1,\"name\":\"Cash\",\"type\":\"CASH\",\"openingBalance\":-12.34,\"archived\":false}"));
            var request = new AccountApi(server.url("/")).create(
                    AccountRepository.json(new AccountDto.Write("Cash", "CASH", new BigDecimal("-12.34"))));
            assertTrue(request.body().isOneShot());
            try (var response = new OkHttpClient().newCall(request).execute()) {
                assertEquals(new BigDecimal("-12.34"), AccountRepository.read(response, AccountDto.class).openingBalance());
            }
            var sent = server.takeRequest();
            assertEquals("POST", sent.getMethod());
            assertEquals("/api/v1/accounts", sent.getPath());
            assertTrue(sent.getBody().readUtf8().contains("\"openingBalance\":-12.34"));
        }
    }

    @Test
    public void reloadReadsArchivedFlagAfterDelete() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.enqueue(new MockResponse().setBody("[{\"id\":1,\"openingBalance\":\"0.00\",\"archived\":true}]"));
            var api = new AccountApi(server.url("/"));
            var client = new OkHttpClient();
            try (var response = client.newCall(api.delete(1)).execute()) {
                AccountRepository.requireSuccess(response);
            }
            try (var response = client.newCall(api.list()).execute()) {
                assertTrue(AccountRepository.rows(response).get(0).archived());
            }
            assertEquals("/api/v1/accounts/1", server.takeRequest().getPath());
            assertEquals("/api/v1/accounts", server.takeRequest().getPath());
        }
    }
}
