package lk.finora.app.data.local;

import android.content.SharedPreferences;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.finora.app.data.api.AuthInterceptor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class TokenStoreTest {
    @Test
    public void successfulCommitPublishesAccessAndPersistsBothTokens() throws Exception {
        PreferencesDouble preferences = new PreferencesDouble(true);
        TokenStore store = new TokenStore(preferences.proxy);
        assertNull(store.accessToken());

        store.save("access", "refresh");

        assertEquals("access", store.accessToken());
        assertEquals(List.of(pair("access", "refresh")), preferences.attempts);
    }

    @Test
    public void failedFirstCommitCannotSupplyBearerEvenWhenCleanupFails() throws Exception {
        PreferencesDouble preferences = new PreferencesDouble(false, false);
        TokenStore store = new TokenStore(preferences.proxy);
        IOException failure = assertThrows(IOException.class, () -> store.save("new", "new-refresh"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of(pair("new", "new-refresh"), Map.of()), preferences.attempts);
        assertNull(store.accessToken());

        assertBearer(store, null);
    }

    @Test
    public void failedReplacementKeepsPreviouslyPublishedSessionAndRestoresBothTokens()
            throws Exception {
        PreferencesDouble preferences = new PreferencesDouble(true, false, true);
        TokenStore store = new TokenStore(preferences.proxy);
        store.save("old", "old-refresh");

        assertThrows(IOException.class, () -> store.save("new", "new-refresh"));

        assertEquals(List.of(pair("old", "old-refresh"), pair("new", "new-refresh"),
                pair("old", "old-refresh")), preferences.attempts);
        assertEquals(pair("old", "old-refresh"), preferences.memory);
        assertBearer(store, "Bearer old");
    }

    @Test
    public void failedRestorationDoesNotChangeThePublishedSession() throws Exception {
        PreferencesDouble preferences = new PreferencesDouble(true, false, false);
        TokenStore store = new TokenStore(preferences.proxy);
        store.save("old", "old-refresh");

        IOException failure = assertThrows(IOException.class, () -> store.save("new", "new-refresh"));

        assertEquals(1, failure.getSuppressed().length);
        assertEquals("old", store.accessToken());
        assertEquals(pair("old", "old-refresh"), preferences.attempts.get(2));
    }

    private static void assertBearer(TokenStore store, String expected) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse());
            HttpUrl url = server.url("/api/v1/me");
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor(server.url("/"), store::accessToken))
                    .build();
            try (var response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                assertEquals(200, response.code());
            }
            var request = server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals(expected, request.getHeader("Authorization"));
        }
    }

    private static Map<String, String> pair(String access, String refresh) {
        return Map.of("access_token", access, "refresh_token", refresh);
    }

    /** Models Android's immediate in-memory update even when the disk commit fails. */
    private static final class PreferencesDouble {
        final ArrayDeque<Boolean> results = new ArrayDeque<>();
        final Map<String, String> memory = new HashMap<>();
        final List<Map<String, String>> attempts = new ArrayList<>();
        final SharedPreferences proxy = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (object, method, args) -> {
                    switch (method.getName()) {
                        case "getString": return memory.getOrDefault(args[0], (String) args[1]);
                        case "edit": return editor();
                        default: throw new AssertionError(method.getName());
                    }
                });

        PreferencesDouble(Boolean... outcomes) {
            results.addAll(List.of(outcomes));
        }

        private SharedPreferences.Editor editor() {
            Map<String, String> changes = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (object, method, args) -> {
                        switch (method.getName()) {
                            case "putString":
                                changes.put((String) args[0], (String) args[1]);
                                return object;
                            case "remove":
                                changes.put((String) args[0], null);
                                return object;
                            case "commit":
                                changes.forEach((key, value) -> {
                                    if (value == null) {
                                        memory.remove(key);
                                    } else {
                                        memory.put(key, value);
                                    }
                                });
                                attempts.add(Map.copyOf(memory));
                                return results.remove();
                            default: throw new AssertionError(method.getName());
                        }
                    });
        }
    }
}
