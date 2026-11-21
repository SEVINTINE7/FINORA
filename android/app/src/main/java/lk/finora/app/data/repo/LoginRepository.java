package lk.finora.app.data.repo;

import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.ApiError;
import lk.finora.app.data.api.AuthApi.LoginRequest;
import lk.finora.app.data.api.AuthApi.LoginResponse;
import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public final class LoginRepository {
    public enum Result {
        SUCCESS, INVALID_CREDENTIALS, ACCOUNT_DISABLED, INVALID_INPUT,
        NETWORK_ERROR, SERVER_ERROR, STORAGE_ERROR, CONFIGURATION_ERROR, SESSION_EXPIRED,
        UNSUPPORTED_ROLE, RESTORE_UNAVAILABLE, CANCELLED
    }

    @FunctionalInterface
    public interface TokenWriter {
        void save(String accessToken, String refreshToken) throws IOException;
    }

    @FunctionalInterface
    public interface SessionWriter {
        void save(String access, String refresh, String role, String language) throws IOException;
    }

    private final ApiClient client;
    private final SessionWriter tokens;

    public LoginRepository(ApiClient client, TokenWriter tokens) {
        this(client, (access, refresh, role, language) -> tokens.save(access, refresh));
    }

    public LoginRepository(ApiClient client, SessionWriter tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    public Result login(String email, String password, Attempt attempt) {
        if (attempt.isCancelled()) {
            return Result.CANCELLED;
        }
        try {
            Call<LoginResponse> call = client.auth().login(new LoginRequest(email, password));
            if (!attempt.start(call)) {
                return Result.CANCELLED;
            }
            Response<LoginResponse> response = call.execute();
            if (!response.isSuccessful()) {
                return errorResult(response);
            }
            LoginResponse session = response.body();
            if (session == null || !hasValue(session.accessToken) || !hasValue(session.refreshToken)
                    || session.expiresIn <= 0 || !hasValue(session.role) || !hasValue(session.language)) {
                return Result.SERVER_ERROR;
            }
            if (!Session.isSupportedRole(session.role)) {
                return Result.UNSUPPORTED_ROLE;
            }
            if (!"EN".equals(session.language) && !"SI".equals(session.language)) {
                return Result.SERVER_ERROR;
            }
            return attempt.save(tokens, session);
        } catch (MalformedJsonException | EOFException exception) {
            return attempt.isCancelled() ? Result.CANCELLED : Result.SERVER_ERROR;
        } catch (IOException exception) {
            return attempt.isCancelled() ? Result.CANCELLED : Result.NETWORK_ERROR;
        } catch (RuntimeException exception) {
            return attempt.isCancelled() ? Result.CANCELLED : Result.SERVER_ERROR;
        } finally {
            attempt.finish();
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Result errorResult(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            ApiError error = body == null ? null : client.parseError(body.string());
            if (error == null || error.code == null) {
                return Result.SERVER_ERROR;
            }
            switch (error.code) {
                case "INVALID_CREDENTIALS": return Result.INVALID_CREDENTIALS;
                case "ACCOUNT_DISABLED": return Result.ACCOUNT_DISABLED;
                case "VALIDATION_ERROR": return Result.INVALID_INPUT;
                default: return Result.SERVER_ERROR;
            }
        } catch (IOException | JsonParseException exception) {
            return Result.SERVER_ERROR;
        }
    }

    /** One request shared by the ViewModel and its worker. */
    public static final class Attempt {
        private boolean cancelled;
        private Call<LoginResponse> call;

        public synchronized void cancel() {
            cancelled = true;
            if (call != null) {
                call.cancel();
            }
        }

        synchronized boolean isCancelled() {
            return cancelled;
        }

        synchronized boolean start(Call<LoginResponse> request) {
            if (cancelled) {
                request.cancel();
                return false;
            }
            call = request;
            return true;
        }

        synchronized void finish() {
            call = null;
        }

        // The lock only orders the start of persistence against cancellation.
        private synchronized boolean beginSave() {
            return !cancelled;
        }

        Result save(TokenWriter writer, LoginResponse session) {
            return save((access, refresh, role, language) -> writer.save(access, refresh), session);
        }

        Result save(SessionWriter writer, LoginResponse session) {
            if (!beginSave()) {
                return Result.CANCELLED;
            }
            try {
                writer.save(session.accessToken, session.refreshToken, session.role, session.language);
                return isCancelled() ? Result.CANCELLED : Result.SUCCESS;
            } catch (IOException | RuntimeException exception) {
                return isCancelled() ? Result.CANCELLED : Result.STORAGE_ERROR;
            }
        }
    }
}
