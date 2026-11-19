package lk.finora.api.bill.dto;

import java.math.BigDecimal;

/** A bill as saved (no month information). */
public record BillResponse(Long id, String name, BigDecimal amount, int dueDay, Long categoryId) {
}
