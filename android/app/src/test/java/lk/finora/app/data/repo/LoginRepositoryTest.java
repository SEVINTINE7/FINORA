package lk.finora.app.data.repo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.AuthApi.LoginResponse;
import lk.finora.app.data.repo.LoginRepository.Attempt;
import lk.finora.app.data.repo.LoginRepository.Result;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LoginRepositoryTest {
    private static final String SESSION = "{\"accessToken\":\"access\","
            + "\"refreshToken\":\"refresh\",\"expiresIn\":1800,\"role\":\"USER\",\"language\":\"EN\"}";

    private MockWebServer server;
    private ApiClient client;
    private final AtomicInteger writes = new AtomicInteger();

    @Before
    public void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new ApiClient(server.url("/").toString(), () -> null);
    }

    @After
    public void stopServer() throws IOException {
        server.close();
    }

    @Test
    public void invalidCredentialsDoesNotWriteTokens() {
        assertRejected(401, "{\"code\":\"INVALID_CREDENTIALS\"}", Result.INVALID_CREDENTIALS);
    }

    @Test
    public void disabledAccountDoesNotWriteTokens() {
        assertRejected(403, "{\"code\":\"ACCOUNT_DISABLED\"}", Result.ACCOUNT_DISABLED);
    }

    @Test
    public void malformedSuccessDoesNotWriteTokens() {
        assertRejected(200, "{\"accessToken\":", Result.SERVER_ERROR);
    }

    @Test
    public void incompleteSuccessDoesNotWriteTokens() {
        assertRejected(200, "{\"accessToken\":\"access\",\"refreshToken\":\"refresh\"}",
                Result.SERVER_ERROR);
    }

    @Test
    public void completeSuccessWritesBothTokensOnce() {
        server.enqueue(new MockResponse().setBody(SESSION));
        LoginRepository repository = new LoginRepository(client, (access, refresh) -> {
            assertEquals("access", access);
            assertEquals("refresh", refresh);
            writes.incrementAndGet();
        });
        assertEquals(Result.SUCCESS, login(repository, new Attempt()));
        assertEquals(1, writes.get());
    }

    @Test
    public void withdrawnLoginKeepsGrantOutOfTokenStoreAndUsesRestrictedHeader() throws Exception {
        String grant = "R".repeat(43);
        server.enqueue(new MockResponse().setBody("""
                {"restrictedToken":"%s","expiresIn":300,"consentVersion":"1.0","language":"EN"}
                """.formatted(grant)));
        var attempt = new Attempt();
        var repository = new LoginRepository(client, (access, refresh) -> writes.incrementAndGet());
        assertEquals(Result.CONSENT_REQUIRED, login(repository, attempt));
        assertEquals(0, writes.get());
        assertEquals(grant, attempt.takeConsentGrant());
        assertEquals(null, attempt.takeConsentGrant());
        server.takeRequest(2, TimeUnit.SECONDS);
        server.enqueue(new MockResponse().setResponseCode(204));
        var recovery = ApiClient.consentRecovery(server.url("/").toString());
        var request = new lk.finora.app.data.api.AuthApi.ConsentAcceptance("1.0", true);
        assertEquals(204, recovery.acceptConsent("Consent " + grant, request).execute().code());
        var sent = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("/api/v1/auth/consent/accept", sent.getPath());
        assertEquals("Consent " + grant, sent.getHeader("Authorization"));
    }

    @Test
    public void failedTokenWriteDoesNotReportSuccess() {
        server.enqueue(new MockResponse().setBody(SESSION));
        LoginRepository repository = new LoginRepository(client, (access, refresh) -> {
            writes.incrementAndGet();
            throw new IOException("Simulated write failure");
        });
        assertEquals(Result.STORAGE_ERROR, login(repository, new Attempt()));
        assertEquals(1, writes.get());
    }

    @Test
    public void cancelledAttemptDoesNotStartRequest() {
        Attempt attempt = new Attempt();
        attempt.cancel();
        assertEquals(Result.CANCELLED, login(recordingRepository(), attempt));
        assertEquals(0, server.getRequestCount());
        assertEquals(0, writes.get());
    }

    @Test
    public void cancellingInFlightInterruptsCallWithoutWritingTokens() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Attempt attempt = new Attempt();
        LoginRepository repository = recordingRepository();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> login(repository, attempt));
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            attempt.cancel();
            assertEquals(Result.CANCELLED, result.get(3, TimeUnit.SECONDS));
            assertEquals(0, writes.get());
        } finally {
            attempt.cancel();
            worker.shutdownNow();
        }
    }

    @Test
    public void cancellationBeforePersistenceRejectsAnAlreadyParsedResponse() {
        LoginResponse session = new LoginResponse();
        session.accessToken = "access";
        session.refreshToken = "refresh";
        Attempt attempt = new Attempt();
        attempt.cancel();
        assertEquals(Result.CANCELLED,
                attempt.save((access, refresh) -> writes.incrementAndGet(), session));
        assertEquals(0, writes.get());
    }

    @Test
    public void cancellingDuringBlockedWriteReturnsAndSuppressesSuccess() throws Exception {
        server.enqueue(new MockResponse().setBody(SESSION));
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        Attempt attempt = new Attempt();
        LoginRepository repository = new LoginRepository(client, (access, refresh) -> {
            writeStarted.countDown();
            try {
                if (!releaseWrite.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Test did not release the write");
                }
            } catch (InterruptedException exception) {
                throw new AssertionError("Write was interrupted", exception);
            }
            writes.incrementAndGet();
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var login = workers.submit(() -> login(repository, attempt));
            assertTrue(writeStarted.await(5, TimeUnit.SECONDS));
            workers.submit(attempt::cancel).get(3, TimeUnit.SECONDS);
            assertEquals(0, writes.get());
            releaseWrite.countDown();
            assertEquals(Result.CANCELLED, login.get(3, TimeUnit.SECONDS));
            assertEquals(1, writes.get());
        } finally {
            releaseWrite.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void assertRejected(int status, String body, Result expected) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
        assertEquals(expected, login(recordingRepository(), new Attempt()));
        assertEquals(0, writes.get());
    }

    private LoginRepository recordingRepository() {
        return new LoginRepository(client, (access, refresh) -> writes.incrementAndGet());
    }

    private static Result login(LoginRepository repository, Attempt attempt) {
        return repository.login("user@example.test", "test-password", attempt);
    }
}
