package lk.finora.api.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** Everything the dashboard screen needs for one month (T20). trend = 6 months, oldest first. */
public record DashboardResponse(String month, BigDecimal income, BigDecimal expense, BigDecimal net,
        List<CategoryTotal> byCategory, List<MonthTotal> trend) {
}
