package lk.finora.api.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
final class AccountDeletionController {
    private final AccountDeletionService deletion;

    AccountDeletionController(AccountDeletionService deletion) { this.deletion = deletion; }

    @DeleteMapping("/api/v1/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Valid @RequestBody Confirmation request) {
        deletion.delete(CurrentUser.id(), request.password());
    }

    record Confirmation(@NotBlank @Size(max = 72) String password) {}
}
