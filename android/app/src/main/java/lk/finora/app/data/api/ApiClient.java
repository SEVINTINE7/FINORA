package lk.finora.app.data.api;

import com.google.gson.Gson;

import java.util.function.Supplier;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private final Gson gson = new Gson();
    private final AuthApi auth;

    public ApiClient(String baseUrl, Supplier<String> accessToken) {
        HttpUrl origin = HttpUrl.get(baseUrl);
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(new AuthInterceptor(origin, accessToken))
                .build();
        auth = new Retrofit.Builder().baseUrl(origin).client(client)
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
