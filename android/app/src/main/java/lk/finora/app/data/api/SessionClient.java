package lk.finora.app.data.api;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.local.TokenStore;

public final class SessionClient {
    private static ApiClient client;

    private SessionClient() {}

    public static synchronized ApiClient get(TokenStore store) {
        if (client == null) {
            client = new ApiClient(BuildConfig.API_BASE_URL, store);
        }
        return client;
    }
}
