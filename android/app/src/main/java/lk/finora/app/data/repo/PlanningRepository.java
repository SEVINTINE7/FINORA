package lk.finora.app.data.repo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public final class PlanningRepository {
    private final ProtectedRequests requests;

    public PlanningRepository(ProtectedRequests requests) {
        this.requests = requests;
    }

    public Call dailyGuide(GuideInput input) { return post("daily-guide", input); }

    public Call simulate(SimulationInput input) { return post("simulate", input); }

    private Call post(String path, Object input) {
        var url = requests.origin().newBuilder().addPathSegments("api/v1").addPathSegment(path).build();
        return requests.call(new Request.Builder().url(url).post(AccountRepository.json(input)).build());
    }

    public static Guide readGuide(Response response) throws IOException {
        var guide = AccountRepository.read(response, Guide.class);
        if (guide.status() == null || guide.asOf() == null || guide.incomeDateSource() == null
                || (guide.trackedUnpaidBills() == null) != (guide.trackedUnpaidBillCount() == null)) {
            throw new IOException("Incomplete guide");
        }
        return guide;
    }

    public static Simulation readSimulation(Response response) throws IOException {
        var result = AccountRepository.read(response, Simulation.class);
        if (result.status() == null || result.goalId() <= 0 || result.hypotheticalMonthlySavings() == null
                || result.remaining() == null || result.spendingReferenceMonth() == null) {
            throw new IOException("Incomplete simulation");
        }
        return result;
    }

    public record GuideInput(BigDecimal availableFunds, BigDecimal plannedSavings, String nextIncomeDate,
                             BigDecimal confirmedTrackedUnpaidBills, BigDecimal additionalUnpaidBills) {
        public GuideInput confirm(BigDecimal amount) {
            return new GuideInput(availableFunds, plannedSavings, nextIncomeDate, amount, additionalUnpaidBills);
        }
    }

    public record Guide(String asOf, String nextIncomeDate, String incomeDateSource, BigDecimal trackedUnpaidBills,
                        Integer trackedUnpaidBillCount, String status, Long spendingDays, BigDecimal dailyAllowance,
                        BigDecimal shortfall, boolean warning) {}

    public record SimulationInput(long goalId, BigDecimal baselineMonthlySavings, BigDecimal monthlyChange,
                                  List<CategoryCut> categoryCuts) {}
    public record CategoryCut(long categoryId, BigDecimal monthlyReduction) {}
    public record Simulation(long goalId, BigDecimal remaining, BigDecimal hypotheticalMonthlySavings,
                             Integer monthsRequired, String hypotheticalFinishDate, String status,
                             String spendingReferenceMonth) {}
}
