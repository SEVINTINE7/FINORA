package lk.finora.app.data.repo;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public final class DashboardRepository {
    private final ProtectedRequests requests;

    public DashboardRepository(ProtectedRequests requests) {
        this.requests = requests;
    }

    public Call load(YearMonth month) {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/dashboard").addQueryParameter("month", month.toString()).build()).build());
    }

    public static Snapshot read(Response response, YearMonth selected) throws IOException {
        Snapshot value = AccountRepository.read(response, Snapshot.class);
        if (!selected.toString().equals(value.month()) || value.byCategory() == null
                || value.trend() == null || value.trend().size() != 6
                || !validTotals(value.income(), value.expense(), value.net())) {
            throw new IOException("Invalid dashboard snapshot");
        }
        for (int i = 0; i < 6; i++) {
            Month row = value.trend().get(i);
            if (row == null || !selected.minusMonths(5 - i).toString().equals(row.month())
                    || !validTotals(row.income(), row.expense(), row.net())) {
                throw new IOException("Invalid dashboard trend");
            }
        }
        BigDecimal categorySum = BigDecimal.ZERO;
        for (Category row : value.byCategory()) {
            if (row == null || !money(row.total()) || row.total().signum() <= 0) {
                throw new IOException("Invalid dashboard category");
            }
            categorySum = categorySum.add(row.total());
        }
        Month last = value.trend().get(5);
        if (categorySum.compareTo(value.expense()) != 0 || last.income().compareTo(value.income()) != 0
                || last.expense().compareTo(value.expense()) != 0) {
            throw new IOException("Inconsistent dashboard snapshot");
        }
        return value;
    }

    private static boolean validTotals(BigDecimal income, BigDecimal expense, BigDecimal net) {
        return money(income) && money(expense) && money(net) && income.signum() >= 0
                && expense.signum() >= 0 && income.subtract(expense).compareTo(net) == 0;
    }

    private static boolean money(BigDecimal value) {
        return value != null && value.scale() <= 2 && value.scale() >= 0;
    }

    public record Snapshot(String month, BigDecimal income, BigDecimal expense, BigDecimal net,
                           List<Category> byCategory, List<Month> trend) {}
    public record Category(Long categoryId, String name, String nameSi, BigDecimal total) {}
    public record Month(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {}
}
