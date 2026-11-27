package lk.finora.app.data.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonParser;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

public class PasswordResetApiTest {
    @Test public void usesTheTwoPublicD64RoutesWithExactBodyKeys() throws Exception {
        try (var server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(202).setBody("{\"message\":\"Accepted\"}"));
            server.enqueue(new MockResponse().setResponseCode(204));
            AuthApi api = new ApiClient(server.url("/").toString(), () -> null).auth();
            assertEquals(202, api.requestPasswordReset(new AuthApi.ForgotPassword("synthetic@example.test"))
                    .execute().code());
            assertEquals(204, api.resetPassword(new AuthApi.ResetPassword(
                    "synthetic@example.test", "012345", "Synthetic2026")).execute().code());
            var request = server.takeRequest();
            assertEquals("/api/v1/auth/password/forgot", request.getPath());
            assertEquals("POST", request.getMethod());
            assertNull(request.getHeader("Authorization"));
            assertEquals(Set.of("email"), JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject().keySet());
            var reset = server.takeRequest();
            assertEquals("/api/v1/auth/password/reset", reset.getPath());
            assertEquals("POST", reset.getMethod());
            assertNull(reset.getHeader("Authorization"));
            var payload = JsonParser.parseString(reset.getBody().readUtf8()).getAsJsonObject();
            assertEquals(Set.of("email", "code", "newPassword"), payload.keySet());
            assertEquals("012345", payload.get("code").getAsString());
        }
    }
}
