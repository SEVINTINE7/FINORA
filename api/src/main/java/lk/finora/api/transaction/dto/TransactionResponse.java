package lk.finora.api.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(Long id, Long accountId, Long categoryId, String type, BigDecimal amount,
        LocalDate txnDate, String merchant, String note, String source) {
}
