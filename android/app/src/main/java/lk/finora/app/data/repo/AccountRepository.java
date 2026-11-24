package lk.finora.app.data.repo;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.AccountApi;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.ApiError;
import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

public final class AccountRepository {
    private final AccountApi api;
    private final ProtectedRequests requests;

    public AccountRepository(ProtectedRequests requests) {
        this.requests = requests;
        api = new AccountApi(requests.origin());
    }

    public Call list() { return requests.call(api.list()); }
    public Call get(long id) { return requests.call(api.get(id)); }
    public Call create(AccountDto.Write body) { return requests.call(api.create(json(body))); }
    public Call update(long id, AccountDto.Write body) { return requests.call(api.update(id, json(body))); }
    public Call delete(long id) { return requests.call(api.delete(id)); }

    public static List<AccountDto> rows(Response response) throws IOException {
        return java.util.Arrays.asList(read(response, AccountDto[].class));
    }

    public static final class ProtectedRequests {
        private final TokenStore store;
        private final Object login;
        private final String role;

        private ProtectedRequests(TokenStore store, Object login, String role) {
            this.store = store;
            this.login = login;
            this.role = role;
        }

        public static ProtectedRequests currentUser() { return currentRole("USER"); }
        public static ProtectedRequests currentStaff() { return currentRole("STAFF"); }

        private static ProtectedRequests currentRole(String role) {
            TokenStore store = TokenStore.current();
            Session session = store == null ? null : store.verifiedSession();
            return session != null && role.equals(session.role)
                    ? new ProtectedRequests(store, session.loginIdentity(), role) : null;
        }

        public boolean isCurrent() {
            Session current = store.verifiedSession();
            return current != null && current.loginIdentity() == login && role.equals(current.role);
        }

        public void addListener(Runnable listener) { store.addSessionListener(listener); }
        public void removeListener(Runnable listener) { store.removeSessionListener(listener); }

        HttpUrl origin() { return HttpUrl.get(BuildConfig.API_BASE_URL); }

        Call call(Request request) {
            synchronized (store) {
                if (!isCurrent()) {
                    throw new IllegalStateException("The initiating login has ended");
                }
                return SessionClient.get(store).newCall(request);
            }
        }
    }

    private static final Gson GSON = new Gson();

    static RequestBody json(Object dto) {
        RequestBody body = RequestBody.create(GSON.toJson(dto), MediaType.get("application/json"));
        return new RequestBody() {
            @Override public boolean isOneShot() { return true; }
            @Override public MediaType contentType() { return body.contentType(); }
            @Override public long contentLength() throws IOException { return body.contentLength(); }
            @Override public void writeTo(BufferedSink sink) throws IOException { body.writeTo(sink); }
        };
    }

    public static void requireSuccess(Response response) throws IOException {
        if (response.isSuccessful()) {
            return;
        }
        ResponseBody body = response.body();
        ApiError error = null;
        if (body != null) {
            try {
                error = GSON.fromJson(body.charStream(), ApiError.class);
            } catch (RuntimeException malformedError) {
                // Retain the HTTP status even when the error body is malformed.
            }
        }
        throw new HttpFailure(response.code(), error == null ? null : error.code,
                error == null || error.fields == null ? java.util.Collections.emptyList() : error.fields);
    }

    public static <T> T read(Response response, Class<T> type) throws IOException {
        requireSuccess(response);
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Missing response body");
        }
        try {
            T value = GSON.fromJson(body.charStream(), type);
            if (value != null) {
                return value;
            }
        } catch (RuntimeException malformedBody) {
            throw new IOException("Invalid response body", malformedBody);
        }
        throw new IOException("Missing response data");
    }

    public static final class HttpFailure extends IOException {
        public final int status;
        public final String code;
        public final List<ApiError.FieldError> fields;
        HttpFailure(int status, String code, List<ApiError.FieldError> fields) {
            super("HTTP " + status);
            this.status = status;
            this.code = code;
            this.fields = fields;
        }
    }
}
