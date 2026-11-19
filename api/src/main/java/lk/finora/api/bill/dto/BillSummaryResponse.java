package lk.finora.api.bill.dto;

import java.math.BigDecimal;

/** Bill totals for one month: total = paid + unpaid. */
public record BillSummaryResponse(String month, BigDecimal total, BigDecimal paid, BigDecimal unpaid) {
}
