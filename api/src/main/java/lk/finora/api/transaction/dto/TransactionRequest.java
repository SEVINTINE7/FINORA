package lk.finora.api.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for creating or editing a transaction. categoryId, merchant, note and source are optional. */
public record TransactionRequest(

        @NotNull
        Long accountId,

        Long categoryId,

        @NotNull @Pattern(regexp = "INCOME|EXPENSE", message = "must be INCOME or EXPENSE")
        String type,

        // LKR, more than zero, at most 2 decimals; fits DECIMAL(15,2)
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull
        LocalDate txnDate,

        @Size(max = 100)
        String merchant,

        @Size(max = 255)
        String note,

        // Default MANUAL when left out
        @Pattern(regexp = "MANUAL|SMS|PASTE|CSV", message = "must be MANUAL, SMS, PASTE or CSV")
        String source) {
}
