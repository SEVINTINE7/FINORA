package lk.finora.api.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lk.finora.api.common.error.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
final class StaffProvisioningController {
    private final StaffProvisioningService staff;

    StaffProvisioningController(StaffProvisioningService staff) { this.staff = staff; }

    @PostMapping("/api/v1/admin/staff")
    ResponseEntity<StaffProvisioningService.Provisioned> create(@Valid @RequestBody ProvisionRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(staff.provision(payload.email(), payload.fullName()));
    }

    @GetMapping("/api/v1/admin/staff")
    List<StaffProvisioningService.Staff> list() { return staff.list(); }

    @PostMapping("/api/v1/admin/staff/{id}/activation")
    ResponseEntity<StaffProvisioningService.Provisioned> reissue(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staff.reissue(id));
    }

    @PatchMapping("/api/v1/admin/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void enable(@PathVariable long id, @RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("enabled").isBoolean()) {
            throw ApiException.badRequest("Expected an enabled boolean");
        }
        staff.setEnabled(id, payload.path("enabled").asBoolean());
    }

    @PostMapping("/api/v1/auth/staff/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@Valid @RequestBody ActivationRequest payload) {
        staff.activate(payload.email(), payload.temporaryPassword(), payload.password());
    }

    record ProvisionRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String fullName) {}

    record ActivationRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{32}") String temporaryPassword,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$") String password) {
        @Override public String toString() { return "Staff activation request"; }
    }
}
