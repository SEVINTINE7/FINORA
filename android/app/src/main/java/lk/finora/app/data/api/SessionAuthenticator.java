package lk.finora.app.data.api;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lk.finora.app.data.api.AuthApi.LoginResponse;
import lk.finora.app.data.api.AuthApi.RefreshRequest;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.Route;

public final class SessionAuthenticator implements Authenticator {
    public enum Outcome { RESTORED, EXPIRED, DISABLED, UNAVAILABLE, STORAGE_ERROR, SUPERSEDED }

    private final HttpUrl origin;
    private final TokenStore store;
    private final AuthApi publicApi;
    private Flight active;

    SessionAuthenticator(HttpUrl origin, TokenStore store, AuthApi publicApi) {
        this.origin = origin;
        this.store = store;
        this.publicApi = publicApi;
    }

    @Override
    public Request authenticate(Route route, okhttp3.Response response) {
        Request request = response.request();
        Session failed = request.tag(Session.class);
        if (response.code() != 401 || failed == null || response.priorResponse() != null
                || !AuthInterceptor.isProtected(origin, request.url())
                || (request.body() != null && (request.body().isOneShot() || request.body().isDuplex()))) {
            return null;
        }
        if (refresh(failed) != Outcome.RESTORED) {
            return null;
        }
        Session replacement = store.verifiedSession();
        if (!failed.sameAccount(replacement) || replacement == failed
                || !failed.role.equals(replacement.role)) {
            return null;
        }
        return request.newBuilder().tag(Session.class, replacement)
                .header("Authorization", "Bearer " + replacement.access).build();
    }

    public Outcome refresh(Session expected) {
        Flight flight;
        boolean startsRequest;
        synchronized (this) {
            Session current = store.session();
            if (!expected.sameAccount(current)) {
                return Outcome.SUPERSEDED;
            }
            if (current != expected) {
                return store.verifiedSession() == current ? Outcome.RESTORED : Outcome.SUPERSEDED;
            }
            startsRequest = active == null || active.session != expected;
            flight = startsRequest ? new Flight(expected) : active;
            active = flight;
        }
        if (!startsRequest) {
            try {
                return flight.result.get(12, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Outcome.UNAVAILABLE;
            } catch (ExecutionException | TimeoutException exception) {
                return Outcome.UNAVAILABLE;
            }
        }
        Outcome outcome = Outcome.UNAVAILABLE;
        try {
            outcome = exchange(expected);
            return outcome;
        } finally {
            flight.result.complete(outcome);
            synchronized (this) {
                if (active == flight) {
                    active = null;
                }
            }
        }
    }

    private Outcome exchange(Session expected) {
        try {
            retrofit2.Response<LoginResponse> response =
                    publicApi.refresh(new RefreshRequest(expected.refresh)).execute();
            if (!response.isSuccessful()) {
                try (ResponseBody body = response.errorBody()) {
                    ApiError error = body == null ? null : new Gson().fromJson(body.string(), ApiError.class);
                    if (error != null && response.code() == 401 && "INVALID_REFRESH_TOKEN".equals(error.code)) {
                        return invalidate(expected, Outcome.EXPIRED);
                    }
                    if (error != null && response.code() == 403 && "ACCOUNT_DISABLED".equals(error.code)) {
                        return invalidate(expected, Outcome.DISABLED);
                    }
                    return Outcome.UNAVAILABLE;
                }
            }

            LoginResponse next = response.body();
            if (next == null || !hasText(next.accessToken) || !hasText(next.refreshToken)
                    || next.expiresIn <= 0 || !Session.isSupportedRole(next.role)
                    || (!"EN".equals(next.language) && !"SI".equals(next.language))) {
                Outcome outcome = invalidate(expected, Outcome.EXPIRED);
                if (next != null && hasText(next.refreshToken)) {
                    revoke(next.refreshToken);
                }
                return outcome;
            }
            try {
                if (store.replace(expected, next.accessToken, next.refreshToken, next.role, next.language) != null) {
                    return Outcome.RESTORED;
                }
                revoke(next.refreshToken);
                return Outcome.SUPERSEDED;
            } catch (IOException | RuntimeException failure) {
                invalidate(expected, Outcome.STORAGE_ERROR);
                revoke(next.refreshToken);
                return Outcome.STORAGE_ERROR;
            }
        } catch (IOException | RuntimeException exception) {
            return Outcome.UNAVAILABLE;
        }
    }

    private Outcome invalidate(Session expected, Outcome outcome) {
        try {
            return store.clear(expected) ? outcome : Outcome.SUPERSEDED;
        } catch (IOException | RuntimeException failure) {
            return Outcome.STORAGE_ERROR;
        }
    }

    public void revoke(String refreshToken) {
        try (ResponseBody body = publicApi.logout(new RefreshRequest(refreshToken)).execute().errorBody()) {
            // Local invalidation has already happened.
        } catch (IOException | RuntimeException ignored) {
            // Revocation is best effort.
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class Flight {
        final Session session;
        final CompletableFuture<Outcome> result = new CompletableFuture<>();

        Flight(Session session) {
            this.session = session;
        }
    }
}
