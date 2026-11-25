package lk.finora.api.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class AdminStatsController {
    private final AdminStatsService stats;

    AdminStatsController(AdminStatsService stats) { this.stats = stats; }

    @GetMapping("/api/v1/admin/stats")
    AdminStatsService.Counts stats() { return stats.read(); }
}
