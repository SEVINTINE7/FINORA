package lk.finora.app.data.api;

import java.math.BigDecimal;

/** A bill in one month from GET /api/v1/bills?month= (T18): when it is due that month and whether it is paid. */
public record BillDto(long id, String name, BigDecimal amount, int dueDay, Long categoryId, String month,
                      String dueDate, boolean paid) {
    /** Body for POST/PUT /api/v1/bills. categoryId is optional (EXPENSE only). */
    public record Write(String name, BigDecimal amount, int dueDay, Long categoryId) {}

    /** A saved bill without month information (GET /api/v1/bills/{id}). */
    public record Saved(long id, String name, BigDecimal amount, int dueDay, Long categoryId) {}

    /** GET /api/v1/bills/summary?month=: total = paid + unpaid. */
    public record Summary(String month, BigDecimal total, BigDecimal paid, BigDecimal unpaid) {}
}
