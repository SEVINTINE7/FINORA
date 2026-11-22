package lk.finora.api.score;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.score.dto.ScoreInputRequest;
import lk.finora.api.score.dto.ScoreInputResponse;
import lk.finora.api.score.dto.ScoreResponse;
import lk.finora.api.security.CurrentUser;

/** USER only via existing security rules. User id always comes from the authenticated principal. */
@RestController
@RequestMapping("/api/v1")
public class ScoreController {
    private final ScoreService service;
    public ScoreController(ScoreService service) { this.service = service; }

    @GetMapping("/score")
    public ScoreResponse score(@RequestParam String month) { return service.forMonth(CurrentUser.id(), month); }

    @GetMapping("/score-inputs/{month}")
    public ScoreInputResponse inputs(@PathVariable String month) { return service.getInputs(CurrentUser.id(), month); }

    @PutMapping("/score-inputs/{month}")
    public ScoreInputResponse put(@PathVariable String month, @Valid @RequestBody ScoreInputRequest request) {
        return service.putInputs(CurrentUser.id(), month, request);
    }
}
