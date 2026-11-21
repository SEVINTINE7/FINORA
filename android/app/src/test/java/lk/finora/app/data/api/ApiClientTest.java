package lk.finora.app.data.api;

import org.junit.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.*;

public class ApiClientTest {
    @Test
    public void validationErrorPreservesFieldDetails() {
        ApiError error = new ApiClient("https://example.com/", () -> null).parseError(
                "{\"status\":400,\"code\":\"VALIDATION_ERROR\","
                + "\"fields\":[{\"field\":\"email\",\"message\":\"must not be blank\"}]}");
        assertEquals(400, error.status);
        assertEquals("VALIDATION_ERROR", error.code);
        assertEquals("email", error.fields.get(0).field);
    }

    @Test
    public void loginUsesPublicEndpointAndDoesNotSendBearerToken() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(
                    "{\"accessToken\":\"access\",\"refreshToken\":\"refresh\","
                    + "\"expiresIn\":1800,\"role\":\"USER\",\"language\":\"EN\"}"));
            ApiClient client = new ApiClient(server.url("/").toString(), () -> "previous");
            AuthApi.LoginResponse session = client.auth()
                    .login(new AuthApi.LoginRequest("user@example.test", "password")).execute().body();
            assertNotNull(session);
            assertEquals(1800, session.expiresIn);
            assertEquals("refresh", session.refreshToken);
            var request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/api/v1/auth/login", request.getPath());
            assertNull(request.getHeader("Authorization"));
            assertEquals("{\"email\":\"user@example.test\",\"password\":\"password\"}",
                    request.getBody().readUtf8());
        }
    }
}
