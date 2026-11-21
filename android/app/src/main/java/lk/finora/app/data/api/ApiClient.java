package lk.finora.app.data.api;

import com.google.gson.Gson;

import java.io.IOException;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private final Gson gson = new Gson();
    private final AuthApi auth;
    private final OkHttpClient http;
    private final SessionAuthenticator sessions;

    public ApiClient(String baseUrl, Supplier<String> accessToken) {
        HttpUrl origin = HttpUrl.get(baseUrl);
        sessions = null;
        http = baseClient().addInterceptor(new AuthInterceptor(origin, accessToken)).build();
        auth = retrofit(origin, http);
    }

    public ApiClient(String baseUrl, TokenStore store) {
        HttpUrl origin = HttpUrl.get(baseUrl);
        AuthApi publicApi = retrofit(origin, baseClient().build());
        sessions = new SessionAuthenticator(origin, store, publicApi);
        http = baseClient().authenticator(sessions)
                .addInterceptor(AuthInterceptor.forSessions(origin, store::verifiedSession))
                .addNetworkInterceptor(chain -> {
                    Session sent = chain.request().tag(Session.class);
                    if (sent != null && sent != store.verifiedSession()) {
                        throw new IOException("Session changed before request could be sent");
                    }
                    return chain.proceed(chain.request());
                })
                .build();
        auth = retrofit(origin, http);
    }

    public SessionAuthenticator sessions() {
        return sessions;
    }

    public OkHttpClient http() {
        return http;
    }

    private static OkHttpClient.Builder baseClient() {
        return new OkHttpClient.Builder()
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(10, TimeUnit.SECONDS);
    }

    private AuthApi retrofit(HttpUrl origin, OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(AuthApi.class);
    }

    public AuthApi auth() {
        return auth;
    }

    public ApiError parseError(String body) {
        return gson.fromJson(body, ApiError.class);
    }
}
