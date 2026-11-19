package lk.finora.api.budget;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.budget.dto.BudgetRequest;
import lk.finora.api.budget.dto.BudgetResponse;
import lk.finora.api.security.CurrentUser;

/** The logged-in user's monthly budgets. USER role only (T06 rules). */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** All budgets of one month (YYYY-MM) with used, remaining and state. */
    @GetMapping
    public List<BudgetResponse> list(@RequestParam String month) {
        return budgetService.list(CurrentUser.id(), month);
    }

    @GetMapping("/{id}")
    public BudgetResponse get(@PathVariable Long id) {
        return budgetService.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@Valid @RequestBody BudgetRequest request) {
        return budgetService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        budgetService.delete(CurrentUser.id(), id);
    }
}
