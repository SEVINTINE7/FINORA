package lk.finora.app.data.repo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;

public final class MonthlyRepository {
    private final ProtectedRequests requests;

    public MonthlyRepository(ProtectedRequests requests) { this.requests = requests; }

    static final long ADVICE_TIMEOUT_SECONDS = 65;

    private HttpUrl.Builder path(String path) {
        return requests.origin().newBuilder().addPathSegments("api/v1").addPathSegment(path);
    }

    public Call history(YearMonth from, YearMonth to) {
        return requests.call(new Request.Builder().url(path("history")
                .addQueryParameter("from", from.toString()).addQueryParameter("to", to.toString()).build()).build());
    }

    public Call advice(YearMonth month) { return advice(month, null); }

    /** D58: goalId is the user's selected focus goal, or null for advice without a goal plan. */
    public Call advice(YearMonth month, Long goalId) {
        HttpUrl.Builder url = path("advice").addQueryParameter("month", month.toString())
                .addQueryParameter("mode", BuildConfig.ADVICE_MODE);
        if (goalId != null) url.addQueryParameter("goalId", Long.toString(goalId));
        Call call = requests.call(new Request.Builder().url(url.build()).build());
        // First delivery may try two providers (each up to 2 x 15 s) before the rule fallback (D58).
        call.timeout().timeout(ADVICE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        return call;
    }

    public Call rate(long id, int value) {
        return requests.call(new Request.Builder().url(path("advice").addPathSegment(Long.toString(id))
                .addPathSegment("rating").build()).post(AccountRepository.json(new Rating(value))).build());
    }

    public record Month(String month, BigDecimal income, BigDecimal expense, BigDecimal net, Integer score,
                        List<Pattern> insights, boolean refreshPending, String reconstructedAt) {}
    public record Pattern(String type, Map<String, BigDecimal> facts) {}
    public record Advice(String month, Long id, String source, boolean verified, List<Item> items, Integer rating) {}
    public record Item(String actionId, String text, List<Fact> numbers) {}
    public record Fact(String factKey, BigDecimal value, String unit, String period) {}
    public record Rating(Integer rating) {}
}
