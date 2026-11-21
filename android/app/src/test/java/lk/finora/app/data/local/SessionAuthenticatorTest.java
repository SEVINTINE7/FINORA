package lk.finora.app.data.local;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.SessionAuthenticator.Outcome;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionAuthenticatorTest {
    private static MockResponse rotated() {
        return new MockResponse().setBody("{\"accessToken\":\"new\",\"refreshToken\":\"rotated\","
                + "\"expiresIn\":1800,\"role\":\"USER\",\"language\":\"SI\"}");
    }

    private static MockResponse error(int status, String code) {
        return new MockResponse().setResponseCode(status).setBody("{\"code\":\"" + code + "\"}");
    }

    private static TokenStore store(Boolean... commits) throws Exception {
        TokenStore store = new TokenStore(new TokenStoreTest.PreferencesDouble(commits).proxy);
        store.save("old", "seed", "USER", "EN");
        return store;
    }

    private static int call(ApiClient client, MockWebServer server) throws Exception {
        try (var response = client.http().newCall(
                new Request.Builder().url(server.url("/api/v1/me")).build()).execute()) {
            return response.code();
        }
    }

    @Test
    public void retryIsBoundedEvenWhenReplacementGets401() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(error(401, "UNAUTHORIZED"));
            server.enqueue(rotated());
            server.enqueue(error(401, "UNAUTHORIZED"));
            TokenStore store = store(true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            assertEquals(401, call(client, server));
            assertEquals(3, server.getRequestCount());
            assertEquals("Bearer old", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
            RecordedRequest refresh = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("/api/v1/auth/refresh", refresh.getPath());
            assertNull(refresh.getHeader("Authorization"));
            assertEquals("{\"refreshToken\":\"seed\"}", refresh.getBody().readUtf8());
            assertEquals("Bearer new", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
            assertEquals("rotated", store.verifiedSession().refresh);
        }
    }

    @Test
    public void twoSimultaneous401sShareOneRotation() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch oldRequests = new CountDownLatch(2);
            AtomicInteger refreshes = new AtomicInteger();
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if ("/api/v1/auth/refresh".equals(request.getPath())) {
                        refreshes.incrementAndGet();
                        return rotated();
                    }
                    if ("Bearer old".equals(request.getHeader("Authorization"))) {
                        oldRequests.countDown();
                        return oldRequests.await(5, TimeUnit.SECONDS)
                                ? error(401, "UNAUTHORIZED") : error(500, "TIMEOUT");
                    }
                    return new MockResponse().setResponseCode("Bearer new".equals(
                            request.getHeader("Authorization")) ? 200 : 403);
                }
            });
            ApiClient client = new ApiClient(server.url("/").toString(), store(true, true));
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                var one = workers.submit(() -> call(client, server));
                var two = workers.submit(() -> call(client, server));
                assertEquals(200, one.get(8, TimeUnit.SECONDS).intValue());
                assertEquals(200, two.get(8, TimeUnit.SECONDS).intValue());
                assertEquals(1, refreshes.get());
            } finally {
                workers.shutdownNow();
            }
        }
    }

    @Test
    public void rejectedRefreshClearsButTemporaryFailurePreserves() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(error(503, "UNAVAILABLE"));
            server.enqueue(error(401, "INVALID_REFRESH_TOKEN"));
            TokenStore store = store(true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            TokenStore.Session initial = store.session();
            assertEquals(Outcome.UNAVAILABLE, client.sessions().refresh(initial));
            assertEquals(initial, store.session());
            assertEquals(Outcome.EXPIRED, client.sessions().refresh(initial));
            assertNull(store.session());
        }
    }

    @Test
    public void failedRotationPersistenceFailsClosed() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(rotated());
            server.enqueue(new MockResponse().setResponseCode(204));
            TokenStore store = store(true, false, true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            assertEquals(Outcome.STORAGE_ERROR, client.sessions().refresh(store.session()));
            assertNull(store.session());
            assertEquals("/api/v1/auth/refresh", server.takeRequest(2, TimeUnit.SECONDS).getPath());
            assertEquals("/api/v1/auth/logout", server.takeRequest(2, TimeUnit.SECONDS).getPath());
        }
    }

    @Test
    public void logoutAndNewLoginBothPreventStalePublication() throws Exception {
        for (boolean newLogin : new boolean[]{false, true}) {
            try (MockWebServer server = new MockWebServer()) {
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                server.setDispatcher(new Dispatcher() {
                    @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                        if ("/api/v1/auth/logout".equals(request.getPath())) {
                            return new MockResponse().setResponseCode(204);
                        }
                        entered.countDown();
                        return release.await(5, TimeUnit.SECONDS) ? rotated() : error(500, "TIMEOUT");
                    }
                });
                TokenStore store = store(true, true);
                TokenStore.Session initial = store.session();
                ApiClient client = new ApiClient(server.url("/").toString(), store);
                ExecutorService worker = Executors.newSingleThreadExecutor();
                try {
                    var result = worker.submit(() -> client.sessions().refresh(initial));
                    assertTrue(entered.await(3, TimeUnit.SECONDS));
                    if (newLogin) {
                        store.save("different", "different-refresh", "STAFF", "EN");
                    } else {
                        store.clearAccount(initial);
                    }
                    release.countDown();
                    assertEquals(Outcome.SUPERSEDED, result.get(5, TimeUnit.SECONDS));
                    assertEquals(newLogin ? "different" : null, store.accessToken());
                    server.takeRequest(2, TimeUnit.SECONDS);
                    RecordedRequest cleanup = server.takeRequest(2, TimeUnit.SECONDS);
                    assertEquals("/api/v1/auth/logout", cleanup.getPath());
                    assertEquals("{\"refreshToken\":\"rotated\"}", cleanup.getBody().readUtf8());
                } finally {
                    release.countDown();
                    worker.shutdownNow();
                }
            }
        }
    }
}
