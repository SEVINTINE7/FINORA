package lk.finora.app.data.repo;

import java.util.Map;
import okhttp3.Call;
import okhttp3.Request;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;

public final class AdminDirectoryRepository {
    private static final String USERS = "/api/v1/admin/users";
    private static final String CATEGORIES = "/api/v1/admin/categories";
    private final ProtectedRequests requests;

    public AdminDirectoryRepository(ProtectedRequests requests) { this.requests = requests; }

    public Call users(String query) {
        var url = requests.origin().newBuilder().encodedPath(USERS).addQueryParameter("q", query).build();
        return requests.call(new Request.Builder().url(url).build());
    }

    public Call changeStatus(long user, String status, String reason) {
        return requests.call(request(USERS + "/" + user + "/status")
                .put(AccountRepository.json(Map.of("status", status, "reason", reason))).build());
    }

    public Call categories() { return requests.call(request(CATEGORIES).build()); }

    public Call saveCategory(Long id, String name, String nameSi, String kind) {
        if (id == null) {
            return requests.call(request(CATEGORIES).post(AccountRepository.json(Map.of(
                    "name", name, "nameSi", nameSi, "kind", kind))).build());
        }
        return requests.call(request(CATEGORIES + "/" + id)
                .put(AccountRepository.json(Map.of("name", name, "nameSi", nameSi))).build());
    }

    public Call removeCategory(long id) {
        return requests.call(request(CATEGORIES + "/" + id).delete().build());
    }

    private Request.Builder request(String path) {
        return new Request.Builder().url(requests.origin().newBuilder().encodedPath(path).build());
    }

    public record User(long id, String email, String name, String status) {}
    public record Category(long id, String name, String nameSi, String kind, boolean active) {}
}
