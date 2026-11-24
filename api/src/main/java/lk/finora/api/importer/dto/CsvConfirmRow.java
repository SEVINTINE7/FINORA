package lk.finora.api.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A preview row the user chose to save. The server checks it again and recomputes the dedup hash. */
public record CsvConfirmRow(

        // File line from the preview, used only in error messages and the response
        @NotNull @Min(1)
        Integer line,

        @NotNull
        LocalDate txnDate,

        @NotNull @Pattern(regexp = "INCOME|EXPENSE", message = "must be INCOME or EXPENSE")
        String type,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull
        Long accountId,

        Long categoryId,

        @Size(max = 100)
        String merchant,

        @Size(max = 255)
        String note,

        @NotNull @Min(1)
        Integer occurrence) {
}
