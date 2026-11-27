package lk.finora.api.auth;

import jakarta.validation.Valid;
import lk.finora.api.auth.dto.ForgotPasswordRequest;
import lk.finora.api.auth.dto.ResetPasswordRequest;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
class PasswordResetController {
    private final PasswordResetService service;

    PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/forgot")
    ResponseEntity<Accepted> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        service.forgot(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new Accepted("If the account is eligible, a reset code will be emailed."));
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        // Throw outside the service transaction so failed-attempt updates are committed.
        if (!service.reset(request.email(), request.code(), request.newPassword())) {
            throw new ApiException(ErrorCodes.INVALID_RESET_CODE, HttpStatus.BAD_REQUEST,
                    "Reset code is invalid or expired");
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    record Accepted(String message) {}
}
