package lk.finora.api.admin;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
final class AdminUserController {
    private final AdminUserService users;

    private final UserPromotionService promotions;

    AdminUserController(AdminUserService users, UserPromotionService promotions) {
        this.users = users;
        this.promotions = promotions;
    }

    @PostMapping("/{id}/promote-to-staff")
    org.springframework.http.ResponseEntity<UserPromotionService.StaffSummary> promote(
            @PathVariable long id, @RequestBody Promotion request) {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(promotions.promote(CurrentUser.id(), id, request.confirm()));
    }

    record Promotion(String confirm) {}

    @GetMapping
    List<UserSummary> list(@RequestParam(defaultValue = "") String q) { return users.search(q); }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeStatus(@PathVariable long id, @Valid @RequestBody StatusChange request) {
        users.changeStatus(CurrentUser.id(), id, request.status(), request.reason());
    }

    record UserSummary(long id, String email, String name, UserStatus status,
                       @com.fasterxml.jackson.annotation.JsonProperty("created_at") Instant createdAt) {}
    record StatusChange(@NotNull UserStatus status, @NotBlank @Size(max = 32) String reason) {}
}
