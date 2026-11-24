package lk.finora.app.data.local;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.SessionAuthenticator.Outcome;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
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
        try (var response = client.newCall(
                new Request.Builder().url(server.url("/api/v1/me")).build()).execute()) {
            return response.code();
        }
    }

    @Test
    public void queuedProtectedCallCannotUseReplacementAccountCredentials() throws Exception {
        CountDownLatch occupied = new CountDownLatch(5);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger unexpected = new AtomicInteger();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if ("/api/v1/me".equals(request.getPath())) {
                        unexpected.incrementAndGet();
                        return new MockResponse();
                    }
                    occupied.countDown();
                    return new MockResponse().setResponseCode(release.await(8, TimeUnit.SECONDS) ? 200 : 500);
                }
            });
            TokenStore store = store(true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            try {
                Request blocker = new Request.Builder().url(server.url("/hold")).build();
                for (int index = 0; index < 5; index++) {
                    enqueue(client.newCall(blocker));
                }
                assertTrue(occupied.await(4, TimeUnit.SECONDS));
                Call queued = client.newCall(new Request.Builder().url(server.url("/api/v1/me")).build());
                var outcome = enqueue(queued);
                store.save("other", "other-refresh", "STAFF", "EN");
                release.countDown();
                assertEquals("Session changed before request could be sent", outcome.get(4, TimeUnit.SECONDS));
                assertEquals(0, unexpected.get());
            } finally {
                release.countDown();
            }
        }
    }

    private static CompletableFuture<String> enqueue(Call call) {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException failure) {
                outcome.complete(failure.getMessage());
            }

            @Override public void onResponse(Call call, Response response) {
                try (response) {
                    outcome.complete("HTTP " + response.code());
                }
            }
        });
        return outcome;
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
    public void rejectedOneShotWriteRefreshesWithoutResendingAndAllowsExplicitRetry() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(error(401, "UNAUTHORIZED"));
            server.enqueue(rotated());
            server.enqueue(new MockResponse().setResponseCode(200));
            TokenStore store = store(true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            var body = new okhttp3.RequestBody() {
                @Override public okhttp3.MediaType contentType() {
                    return okhttp3.MediaType.get("application/json");
                }
                @Override public boolean isOneShot() { return true; }
                @Override public void writeTo(okio.BufferedSink sink) throws IOException {
                    sink.writeUtf8("{}");
                }
            };
            var write = new Request.Builder().url(server.url("/api/v1/transactions/batch")).post(body).build();

            try (var response = client.newCall(write).execute()) {
                assertEquals(401, response.code());
            }
            assertEquals(2, server.getRequestCount());
            assertEquals("Bearer old", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
            assertEquals("/api/v1/auth/refresh", server.takeRequest(2, TimeUnit.SECONDS).getPath());

            try (var response = client.newCall(write).execute()) {
                assertEquals(200, response.code());
            }
            assertEquals(3, server.getRequestCount());
            assertEquals("Bearer new", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
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
    public void late401FromFailedRefreshWaveDoesNotRefreshAgain() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            CountDownLatch delayedRequest = new CountDownLatch(1);
            CountDownLatch releaseDelayed = new CountDownLatch(1);
            AtomicInteger refreshes = new AtomicInteger();
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if ("/api/v1/auth/refresh".equals(request.getPath())) {
                        return refreshes.incrementAndGet() == 1 ? error(503, "UNAVAILABLE") : rotated();
                    }
                    if ("/api/v1/me?late=true".equals(request.getPath())) {
                        delayedRequest.countDown();
                        if (!releaseDelayed.await(5, TimeUnit.SECONDS)) {
                            return error(500, "TIMEOUT");
                        }
                    }
                    return "Bearer new".equals(request.getHeader("Authorization"))
                            ? new MockResponse() : error(401, "UNAUTHORIZED");
                }
            });
            ApiClient client = new ApiClient(server.url("/").toString(), store(true, true));
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                var delayed = worker.submit(() -> {
                    Request request = new Request.Builder().url(server.url("/api/v1/me?late=true")).build();
                    try (var response = client.newCall(request).execute()) {
                        return response.code();
                    }
                });
                assertTrue(delayedRequest.await(3, TimeUnit.SECONDS));
                assertEquals(401, call(client, server));
                releaseDelayed.countDown();
                assertEquals(401, delayed.get(5, TimeUnit.SECONDS).intValue());
                assertEquals(1, refreshes.get());
                assertEquals(200, call(client, server));
                assertEquals(2, refreshes.get());
            } finally {
                releaseDelayed.countDown();
                worker.shutdownNow();
            }
        }
    }

    @Test
    public void rotatingRefreshIgnoresAutomatic503Replay() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(error(503, "UNAVAILABLE").setHeader("Retry-After", "0"));
            server.enqueue(rotated());
            TokenStore store = store(true, true);
            ApiClient client = new ApiClient(server.url("/").toString(), store);
            assertEquals(Outcome.UNAVAILABLE, client.sessions().refresh(store.session()));
            assertEquals(1, server.getRequestCount());
            assertEquals("seed", store.session().refresh);
            assertEquals(Outcome.RESTORED, client.sessions().refresh(store.session()));
            assertEquals(2, server.getRequestCount());
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
