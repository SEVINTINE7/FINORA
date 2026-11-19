package lk.finora.api.account.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for creating or editing an account. */
public record AccountRequest(

        @NotBlank @Size(max = 60)
        String name,

        @NotNull @Pattern(regexp = "BANK|CASH|CARD|WALLET", message = "must be BANK, CASH, CARD or WALLET")
        String type,

        // Fits DECIMAL(15,2). May be negative, e.g. a credit card that already has money owed
        @NotNull @Digits(integer = 13, fraction = 2)
        BigDecimal openingBalance) {
}
