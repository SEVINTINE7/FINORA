package lk.finora.app.data.repo;

import java.io.IOException;
import java.util.Map;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/** T34: alert opt-outs on /api/v1/me and this phone's push token on /api/v1/devices. */
public final class AlertRepository {
    private final ProtectedRequests requests;

    public AlertRepository(ProtectedRequests requests) {
        this.requests = requests;
    }

    /** GET /api/v1/me; read the alerts part with {@link #settings(Response)}. */
    public Call load() {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/me").build()).get().build());
    }

    /** PUT /api/v1/me/alerts with all three flags; the response is the updated profile. */
    public Call save(Settings settings) {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/me/alerts").build())
                .put(AccountRepository.json(settings)).build());
    }

    /** POST /api/v1/devices: 204. Registering the same token again changes nothing. */
    public Call registerDevice(String token) {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/devices").build())
                .post(AccountRepository.json(Map.of("token", token))).build());
    }

    public static Settings settings(Response response) throws IOException {
        Me me = AccountRepository.read(response, Me.class);
        if (me.alerts == null) throw new IOException("Missing alert settings");
        return me.alerts;
    }

    /** true = the user wants that alert type. */
    public static final class Settings {
        public final boolean budget;
        public final boolean bill;
        public final boolean goal;

        public Settings(boolean budget, boolean bill, boolean goal) {
            this.budget = budget;
            this.bill = bill;
            this.goal = goal;
        }
    }

    private static final class Me {
        Settings alerts;
    }
}
