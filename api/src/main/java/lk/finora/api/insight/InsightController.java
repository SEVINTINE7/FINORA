package lk.finora.api.insight;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lk.finora.api.insight.dto.InsightResponse;
import lk.finora.api.security.CurrentUser;

/** The logged-in user's spending patterns for one month. USER role only (T06 rules). */
@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping
    public List<InsightResponse> list(@RequestParam String month) {
        return insightService.forMonth(CurrentUser.id(), month);
    }
}
