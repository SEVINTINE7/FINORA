package lk.finora.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/password")
class PasswordResetController {
    record Forgot(@NotBlank @Email @Size(max = 254) String email) {
        @Override public String toString() { return "Password reset request"; }
    }
    record Reset(@NotBlank @Email @Size(max = 254) String email, String code,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$") String newPassword) {
        @Override public String toString() { return "Password reset confirmation"; }
    }

    private final PasswordResetService service;

    private final PasswordResetRequests requests;

    PasswordResetController(PasswordResetService service, PasswordResetRequests requests) {
        this.requests = requests;
        this.service = service;
    }

    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgot(@Valid @RequestBody Forgot request) {
        requests.submit(() -> service.issue(request.email()));
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody Reset request) {
        if (request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password exceeds the BCrypt input limit");
        }
        if (!service.reset(request.email(), request.code(), request.newPassword())) {
            throw new ApiException("INVALID_RESET_CODE", HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
        }
    }
}
