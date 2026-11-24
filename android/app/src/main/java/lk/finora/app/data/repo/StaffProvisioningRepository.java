package lk.finora.app.data.repo;

import java.util.Map;
import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class StaffProvisioningRepository {
    public record Staff(long id, String email, String fullName, String status, boolean pending) {}
    public record Provisioned(long id, String temporaryPassword) {
        @Override public String toString() { return "Provisioned staff"; }
    }

    private final ProtectedRequests requests;
    public StaffProvisioningRepository(ProtectedRequests requests) { this.requests = requests; }

    public Call list() { return requests.call(admin("").build()); }
    public Call provision(String email, String fullName) {
        return requests.call(admin("").post(AccountRepository.json(Map.of("email", email, "fullName", fullName))).build());
    }
    public Call reissue(long id) {
        return requests.call(admin("/" + id + "/activation").post(RequestBody.create(new byte[0])).build());
    }
    public Call setEnabled(long id, boolean enabled) {
        return requests.call(admin("/" + id).patch(AccountRepository.json(Map.of("enabled", enabled))).build());
    }
    public static Call activate(String email, String temporary, String password) {
        var origin = HttpUrl.get(BuildConfig.API_BASE_URL);
        var url = origin.newBuilder().encodedPath("/api/v1/auth/staff/activate").build();
        return new ApiClient(BuildConfig.API_BASE_URL, () -> null).newCall(new Request.Builder().url(url)
                .post(AccountRepository.json(Map.of("email", email, "temporaryPassword", temporary,
                        "password", password))).build());
    }
    private Request.Builder admin(String path) {
        return new Request.Builder().url(requests.origin().newBuilder().encodedPath("/api/v1/admin/staff" + path).build());
    }
}
