package lk.finora.app.data.repo;

import org.junit.Test;

import lk.finora.app.data.api.CategoryApi;
import lk.finora.app.data.api.CategoryDto;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CategoryRepositoryTest {
    @Test
    public void updateOmitsKindAndKeepsOptionalValues() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"id\":4,\"name\":\"Food\",\"kind\":\"EXPENSE\",\"isDefault\":false}"));
            var api = new CategoryApi(server.url("/"));
            var update = new CategoryDto.Update("Food", "Food SI", "NEED");
            try (var response = new OkHttpClient().newCall(api.update(4, AccountRepository.json(update))).execute()) {
                assertFalse(AccountRepository.read(response, CategoryDto.class).isDefault());
            }
            var sent = server.takeRequest();
            assertEquals("PUT", sent.getMethod());
            assertEquals("/api/v1/categories/4", sent.getPath());
            String body = sent.getBody().readUtf8();
            assertTrue(body.contains("\"nameSi\":\"Food SI\""));
            assertTrue(body.contains("\"needOrWant\":\"NEED\""));
            assertFalse(body.contains("\"kind\""));
        }
    }

    @Test
    public void conflictRetainsCodeForLocalizedFeedback() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"code\":\"IN_USE\"}"));
            try (var response = new OkHttpClient().newCall(new CategoryApi(server.url("/")).delete(1)).execute()) {
                var error = assertThrows(AccountRepository.HttpFailure.class,
                        () -> AccountRepository.requireSuccess(response));
                assertEquals("IN_USE", error.code);
                assertEquals(409, error.status);
            }
        }
    }
}
