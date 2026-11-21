package lk.finora.app.data.api;

import java.io.IOException;
import java.util.function.Supplier;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {
    private final HttpUrl origin;
    private final Supplier<String> accessToken;

    public AuthInterceptor(HttpUrl origin, Supplier<String> accessToken) {
        this.origin = origin;
        this.accessToken = accessToken;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        Request.Builder builder = request.newBuilder().removeHeader("Authorization");
        if (origin.scheme().equals(url.scheme()) && origin.host().equals(url.host())
                && origin.port() == url.port() && url.encodedPath().startsWith("/api/v1/")
                && !url.encodedPath().startsWith("/api/v1/auth/")) {
            String token = accessToken.get();
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        return chain.proceed(builder.build());
    }
}
