package lk.finora.app.data.repo;

import com.google.gson.JsonParseException;

import java.io.IOException;

import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.ApiError;
import lk.finora.app.data.api.AuthApi.RegisterRequest;
import lk.finora.app.data.api.AuthApi.RegisterResponse;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Creates a USER account (T07.4). Runs on a worker thread; never stores tokens. */
public final class RegisterRepository {
    /** Consent text version shown on the register screen (D12). Bump with the text. */
    public static final String CONSENT_VERSION = "1.0";

    public enum Result { SUCCESS, EMAIL_TAKEN, UNDER_18, INVALID_INPUT, NETWORK_ERROR, SERVER_ERROR }

    private final ApiClient client;

    public RegisterRepository(ApiClient client) {
        this.client = client;
    }

    public Result register(String email, String password, String fullName, String language, boolean over18) {
        try {
            Response<RegisterResponse> response = client.auth().register(new RegisterRequest(
                    email, password, fullName, language, over18, CONSENT_VERSION)).execute();
            if (response.isSuccessful()) {
                RegisterResponse body = response.body();
                return body != null && body.userId > 0 ? Result.SUCCESS : Result.SERVER_ERROR;
            }
            return errorResult(response);
        } catch (IOException exception) {
            return Result.NETWORK_ERROR;
        } catch (RuntimeException exception) {
            return Result.SERVER_ERROR;
        }
    }

    private Result errorResult(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            ApiError error = body == null ? null : client.parseError(body.string());
            return fromCode(error == null ? null : error.code);
        } catch (IOException | JsonParseException exception) {
            return Result.SERVER_ERROR;
        }
    }

    static Result fromCode(String code) {
        if (code == null) return Result.SERVER_ERROR;
        switch (code) {
            case "EMAIL_TAKEN": return Result.EMAIL_TAKEN;
            case "UNDER_18": return Result.UNDER_18;
            case "VALIDATION_ERROR": return Result.INVALID_INPUT;
            default: return Result.SERVER_ERROR;
        }
    }
}
