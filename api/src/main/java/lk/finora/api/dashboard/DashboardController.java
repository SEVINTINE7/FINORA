package lk.finora.api.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lk.finora.api.dashboard.dto.DashboardResponse;
import lk.finora.api.security.CurrentUser;

/** Dashboard numbers for the logged-in user. USER role only (T06 rules). */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** month = YYYY-MM; leave it out for the current month. */
    @GetMapping
    public DashboardResponse get(@RequestParam(required = false) String month) {
        return dashboardService.get(CurrentUser.id(), month);
    }
}
