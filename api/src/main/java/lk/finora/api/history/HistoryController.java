package lk.finora.api.history;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import lk.finora.api.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }

    @GetMapping
    public List<HistoryService.Month> history(@RequestParam String from, @RequestParam String to) {
        return service.read(CurrentUser.id(), from, to);
    }
}
