package lk.finora.api.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.goal.GoalService;
import lk.finora.api.goal.GoalStatus;
import lk.finora.api.goal.dto.GoalResponse;
import lk.finora.api.planning.dto.SimulationRequest;
import lk.finora.api.planning.dto.SimulationResponse;
import lk.finora.api.transaction.TransactionRepository;

@Service
public class SimulationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private final GoalService goals;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public SimulationService(
            GoalService goals,
            CategoryRepository categories,
            TransactionRepository transactions) {
        this.goals = goals;
        this.categories = categories;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public SimulationResponse simulate(Long userId, SimulationRequest request) {

        // Validate before any request dereference or database access.
        if (request == null
                || request.goalId() == null
                || request.baselineMonthlySavings() == null
                || request.monthlyChange() == null
                || request.categoryCuts() == null) {
            throw ApiException.badRequest("Simulation inputs are required");
        }

        if (request.goalId() <= 0
                || request.baselineMonthlySavings().signum() < 0
                || request.baselineMonthlySavings().scale() > 2
                || request.monthlyChange().scale() > 2
                || request.categoryCuts().size() > 20) {
            throw ApiException.badRequest("Invalid simulation inputs");
        }

        // Ownership is enforced by GoalService.get().
        // Foreign and nonexistent goals both return 404.
        GoalResponse goal = goals.get(userId, request.goalId());

        if (goal.status() == GoalStatus.CANCELLED) {
            throw ApiException.badRequest("Cancelled goals cannot be simulated");
        }

        LocalDate today = LocalDate.now(ZONE);
        YearMonth reference = YearMonth.from(today).minusMonths(1);

        BigDecimal totalCuts = new BigDecimal("0.00");
        Set<Long> seen = new HashSet<>();

        for (SimulationRequest.CategoryCut cut : request.categoryCuts()) {
            if (cut == null
                    || cut.categoryId() == null
                    || cut.categoryId() <= 0
                    || cut.monthlyReduction() == null
                    || cut.monthlyReduction().signum() <= 0
                    || cut.monthlyReduction().scale() > 2) {
                throw ApiException.badRequest("Invalid category cut");
            }
            if (!seen.add(cut.categoryId())) {
                throw ApiException.badRequest(
                        "The same category cannot be reduced twice");
            }

            Category category = categories
                    .findVisibleById(cut.categoryId(), userId)
                    .orElseThrow(() ->
                            ApiException.notFound("Category not found"));

            if (!"EXPENSE".equals(category.getKind().name())) {
                throw ApiException.badRequest(
                        "Only expense categories can be reduced");
            }

            BigDecimal recorded = transactions.sumExpenseForCategory(
                    userId,
                    cut.categoryId(),
                    reference.atDay(1),
                    reference.atEndOfMonth());

            if (recorded == null) {
                recorded = BigDecimal.ZERO;
            }

            if (cut.monthlyReduction().compareTo(recorded) > 0) {
                throw ApiException.badRequest(
                        "Category reduction exceeds recorded spending in the reference month");
            }

            totalCuts = totalCuts.add(cut.monthlyReduction());
        }

        // Bean Validation normally rejects invalid values before this method.
        // These guards also protect direct service callers.
        if (request.baselineMonthlySavings() == null
                || request.monthlyChange() == null
                || request.categoryCuts() == null) {
            throw ApiException.badRequest("Simulation inputs are required");
        }

        if (request.baselineMonthlySavings().signum() < 0
                || totalCuts.signum() < 0) {
            throw ApiException.badRequest("Invalid simulation amounts");
        }

        SimulationMath.Result result = SimulationMath.calculate(
                goal.remaining(),
                request.baselineMonthlySavings(),
                request.monthlyChange(),
                totalCuts,
                today);

        return new SimulationResponse(
                goal.id(),
                goal.targetAmount(),
                goal.saved(),
                goal.remaining(),
                request.baselineMonthlySavings().setScale(2, RoundingMode.UNNECESSARY),
                request.monthlyChange().setScale(2, RoundingMode.UNNECESSARY),
                totalCuts.setScale(2, RoundingMode.UNNECESSARY),
                result.monthlyAmount(),
                result.monthsRequired(),
                result.finishDate(),
                result.status(),
                reference.toString(),
                List.of(
                        "This is a hypothetical calculation, not a forecast.",
                        "Baseline savings are explicitly supplied by the user.",
                        "Monthly change must exclude the listed category reductions.",
                        "Category reductions are compared with the previous completed month's recorded expenses.",
                        "The first assumed contribution occurs at the end of next month.",
                        "No financial records are saved or modified."));
    }
}