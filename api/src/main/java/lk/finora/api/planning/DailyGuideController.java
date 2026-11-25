package lk.finora.api.planning;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import lk.finora.api.planning.dto.DailyGuideRequest;
import lk.finora.api.planning.dto.DailyGuideResponse;
import lk.finora.api.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class DailyGuideController {
    private final DailyGuideService service;
    public DailyGuideController(DailyGuideService service) { this.service = service; }
    @PostMapping("/daily-guide")
    public DailyGuideResponse calculate(@Valid @RequestBody DailyGuideRequest request) {
        return service.calculate(CurrentUser.id(), request);
    }
}
