package lk.finora.app.data.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AuthInterceptorTest {
    @Test
    public void sendsBearerOnlyToProtectedPathsOnConfiguredOrigin() throws Exception {
        try (MockWebServer api = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(api.url("/"), () -> "test-token")).build();

            api.enqueue(new MockResponse());
            try (var response = client.newCall(new Request.Builder()
                    .url(api.url("/api/v1/me")).build()).execute()) {
                assertEquals(200, response.code());
            }
            assertEquals("Bearer test-token", api.takeRequest().getHeader("Authorization"));

            other.enqueue(new MockResponse());
            try (var response = client.newCall(new Request.Builder().url(other.url("/api/v1/me"))
                    .header("Authorization", "Bearer previous").build()).execute()) {
                assertEquals(200, response.code());
            }
            assertNull(other.takeRequest().getHeader("Authorization"));
        }
    }
}
