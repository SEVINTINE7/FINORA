package lk.finora.app.data.repo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Request;

public final class InsightsRepository {
    private final ProtectedRequests requests;
    public InsightsRepository(ProtectedRequests requests) { this.requests = requests; }

    public Call insights(YearMonth month) { return query("insights", month); }
    public Call score(YearMonth month) { return query("score", month); }
    public Call declaration(YearMonth month) { return declaration(month, null); }
    public Call declaration(YearMonth month, Declaration values) {
        var request = new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/score-inputs").addPathSegment(month.toString()).build());
        return requests.call((values == null ? request : request.put(AccountRepository.json(values))).build());
    }

    private Call query(String path, YearMonth month) {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1").addPathSegment(path)
                .addQueryParameter("month", month.toString()).build()).build());
    }

    public record Insight(String type, String month, String from, String to, Map<String, BigDecimal> facts) {}
    public record Score(String month, String modelVersion, Integer total, String reserveAsOf, Part[] parts) {}
    public record Part(String key, int weight, String status, BigDecimal points, String reasonKey,
                       Map<String, BigDecimal> facts) {}
    public record Declaration(BigDecimal emergencyReserve, String reserveAsOf,
                              Boolean noApplicableDebt, Boolean noApplicableGoals) {}
    public record DeclarationResult(String month, BigDecimal emergencyReserve, String reserveAsOf,
                                    Boolean noApplicableDebt, Boolean noApplicableGoals) {
        public Declaration values() {
            return new Declaration(emergencyReserve, reserveAsOf, noApplicableDebt, noApplicableGoals);
        }
    }
}
