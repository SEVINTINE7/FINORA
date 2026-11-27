package lk.finora.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email,
        String code,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$", message = "must contain at least one letter and one digit")
        String newPassword) {
    @Override public String toString() { return "Reset password request"; }
}
