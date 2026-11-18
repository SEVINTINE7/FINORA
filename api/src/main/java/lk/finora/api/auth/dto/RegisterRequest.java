package lk.finora.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank @Email @Size(max = 254)
        String email,

        // Decision D09: at least 8 characters with at least one letter and one digit (BCrypt reads at most 72)
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$", message = "must contain at least one letter and one digit")
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        @NotNull @Pattern(regexp = "EN|SI", message = "must be EN or SI")
        String language,

        @NotNull
        Boolean over18,

        @NotBlank
        String consentVersion) {
}
