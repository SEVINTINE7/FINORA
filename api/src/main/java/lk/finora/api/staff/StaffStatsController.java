package lk.finora.api.staff;

import lk.finora.api.security.CurrentUser;
import lk.finora.api.staff.dto.StaffStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns only support queue counts for the authenticated staff member. */
@RestController
final class StaffStatsController {
    private final StaffStatsService stats;

    StaffStatsController(StaffStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/api/v1/staff/stats")
    StaffStatsResponse stats() {
        return stats.read(CurrentUser.id());
    }
}
