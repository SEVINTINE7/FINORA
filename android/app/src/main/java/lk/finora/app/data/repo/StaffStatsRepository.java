package lk.finora.app.data.repo;

import okhttp3.Call;
import okhttp3.Request;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;

public final class StaffStatsRepository {
    public record Stats(Long myOpenTickets, Long unassignedTickets) {}

    private final ProtectedRequests requests;

    public StaffStatsRepository(ProtectedRequests requests) {
        this.requests = requests;
    }

    public Call fetch() {
        var url = requests.origin().newBuilder().encodedPath("/api/v1/staff/stats").build();
        return requests.call(new Request.Builder().url(url).build());
    }
}
