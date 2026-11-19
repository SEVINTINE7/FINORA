package lk.finora.api.bill.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for creating or editing a bill. categoryId is optional. */
public record BillRequest(

        @NotBlank @Size(max = 60)
        String name,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull @Min(1) @Max(31)
        Integer dueDay,

        Long categoryId) {
}
