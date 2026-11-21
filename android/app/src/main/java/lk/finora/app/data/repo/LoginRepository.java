package lk.finora.app.data.repo;

import com.google.gson.JsonParseException;

import java.io.IOException;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.ApiError;
import lk.finora.app.data.api.AuthApi.LoginRequest;
import lk.finora.app.data.api.AuthApi.LoginResponse;
import lk.finora.app.data.local.TokenStore;
import okhttp3.ResponseBody;
import retrofit2.Response;

public final class LoginRepository {
    public enum Result {
        SUCCESS, INVALID_CREDENTIALS, ACCOUNT_DISABLED, INVALID_INPUT,
        NETWORK_ERROR, SERVER_ERROR, STORAGE_ERROR, CONFIGURATION_ERROR
    }

    private final ApiClient client;
    private final TokenStore tokens;

    public LoginRepository(ApiClient client, TokenStore tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    public Result login(String email, String password) {
        Response<LoginResponse> response;
        try {
            response = client.auth().login(new LoginRequest(email, password)).execute();
        } catch (IOException exception) {
            return Result.NETWORK_ERROR;
        } catch (RuntimeException exception) {
            return Result.SERVER_ERROR;
        }

        if (!response.isSuccessful()) {
            return errorResult(response);
        }
        LoginResponse session = response.body();
        if (session == null || session.accessToken == null || session.accessToken.trim().isEmpty()
                || session.refreshToken == null || session.refreshToken.trim().isEmpty()) {
            return Result.SERVER_ERROR;
        }
        try {
            tokens.save(session.accessToken, session.refreshToken);
            return Result.SUCCESS;
        } catch (IOException | RuntimeException exception) {
            return Result.STORAGE_ERROR;
        }
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
}
