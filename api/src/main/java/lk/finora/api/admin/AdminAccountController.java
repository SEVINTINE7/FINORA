package lk.finora.api.admin;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** T59 (D62): ADMIN creates, lists, disables and re-enables other ADMIN accounts. ADMIN only (T06). */
@RestController
@RequestMapping("/api/v1/admin/admins")
final class AdminAccountController {
    private final AdminAccountService admins;

    AdminAccountController(AdminAccountService admins) { this.admins = admins; }

    @PostMapping
    ResponseEntity<AdminSummary> create(@Valid @RequestBody CreateAdmin request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(admins.create(CurrentUser.id(), request.email(), request.fullName(), request.password()));
    }

    @GetMapping
    List<AdminSummary> list() { return admins.list(); }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setEnabled(@PathVariable long id, @RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("enabled").isBoolean()) {
            throw ApiException.badRequest("Expected an enabled boolean");
        }
        admins.setEnabled(CurrentUser.id(), id, payload.path("enabled").asBoolean());
    }

    /** D09 password rule, as at registration. */
    record CreateAdmin(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$", message = "must contain at least one letter and one digit")
            String password) {
        @Override public String toString() { return "Admin creation request"; }
    }

    record AdminSummary(long id, String email, String fullName, boolean enabled, Instant createdAt) {}
}
