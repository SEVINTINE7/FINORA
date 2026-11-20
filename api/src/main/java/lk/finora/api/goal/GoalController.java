package lk.finora.api.goal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.goal.dto.ContributionRequest;
import lk.finora.api.goal.dto.ContributionResponse;
import lk.finora.api.goal.dto.GoalRequest;
import lk.finora.api.goal.dto.GoalResponse;
import lk.finora.api.security.CurrentUser;

/** The logged-in user's savings goals and contributions. USER role only (T06 rules). */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> list() {
        return goalService.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable Long id) {
        return goalService.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody GoalRequest request) {
        return goalService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest request) {
        return goalService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        goalService.delete(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> listContributions(@PathVariable Long id) {
        return goalService.listContributions(CurrentUser.id(), id);
    }

    /** Returns the goal with its new progress. */
    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse addContribution(@PathVariable Long id, @Valid @RequestBody ContributionRequest request) {
        return goalService.addContribution(CurrentUser.id(), id, request);
    }

    /** Returns the goal with its new progress. */
    @DeleteMapping("/{id}/contributions/{contributionId}")
    public GoalResponse deleteContribution(@PathVariable Long id, @PathVariable Long contributionId) {
        return goalService.deleteContribution(CurrentUser.id(), id, contributionId);
    }
}
