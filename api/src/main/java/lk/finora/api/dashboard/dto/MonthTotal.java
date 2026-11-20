package lk.finora.api.dashboard.dto;

import java.math.BigDecimal;

/** One bar of the trend chart. net = income - expense. */
public record MonthTotal(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {
}
