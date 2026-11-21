package lk.finora.app.data.api;

import java.io.IOException;
import java.util.function.Supplier;

import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {
    private final HttpUrl origin;
    private final Supplier<String> accessToken;
    private final Supplier<Session> sessions;

    public AuthInterceptor(HttpUrl origin, Supplier<String> accessToken) {
        this(origin, accessToken, () -> null);
    }

    private AuthInterceptor(HttpUrl origin, Supplier<String> accessToken, Supplier<Session> sessions) {
        this.origin = origin;
        this.accessToken = accessToken;
        this.sessions = sessions;
    }

    public static AuthInterceptor forSessions(HttpUrl origin, Supplier<Session> sessions) {
        return new AuthInterceptor(origin, () -> null, sessions);
    }

    static boolean isProtected(HttpUrl origin, HttpUrl url) {
        return origin.scheme().equals(url.scheme()) && origin.host().equals(url.host())
                && origin.port() == url.port() && url.encodedPath().startsWith("/api/v1/")
                && !url.encodedPath().startsWith("/api/v1/auth/");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        Request.Builder builder = request.newBuilder().removeHeader("Authorization").tag(Session.class, null);
        if (isProtected(origin, url)) {
            Session session = sessions.get();
            String token = session == null ? accessToken.get() : session.access;
            builder.tag(Session.class, session);
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        return chain.proceed(builder.build());
    }
}
