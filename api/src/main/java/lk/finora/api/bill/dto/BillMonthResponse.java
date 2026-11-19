package lk.finora.api.bill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A bill in one month: when it is due that month and whether it is paid. */
public record BillMonthResponse(Long id, String name, BigDecimal amount, int dueDay, Long categoryId, String month,
        LocalDate dueDate, boolean paid) {
}
