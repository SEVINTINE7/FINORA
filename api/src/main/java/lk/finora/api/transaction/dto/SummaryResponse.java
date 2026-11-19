package lk.finora.api.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Totals for a date range. net = income - expense (can be negative). */
public record SummaryResponse(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expense, BigDecimal net) {
}
