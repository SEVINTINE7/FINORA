package lk.finora.api.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.budget.dto.BudgetRequest;
import lk.finora.api.budget.dto.BudgetResponse;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryKind;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.transaction.TransactionRepository;

/** Monthly budgets per expense category (T17). Every method takes the caller's id. */
@Service
public class BudgetService {

    // NEAR starts when 80% of the limit is used (T17.2)
    private static final BigDecimal NEAR_PERCENT = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    private final lk.finora.api.account.FinanceWriteLock owners;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
            TransactionRepository transactionRepository, lk.finora.api.account.FinanceWriteLock owners) {
        this.owners = owners;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(Long userId, String month) {
        if (month == null || !month.matches(BudgetRequest.MONTH_PATTERN)) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        List<BudgetResponse> result = new ArrayList<>();
        for (Budget budget : budgetRepository.findByUserIdAndMonthOrderById(userId, month)) {
            result.add(toResponse(budget));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(Long userId, Long id) {
        return toResponse(findOwn(userId, id));
    }

    @Transactional
    public BudgetResponse create(Long userId, BudgetRequest request) {
        owners.acquire(userId);
        checkCategory(userId, request.categoryId());
        if (budgetRepository.existsByUserIdAndCategoryIdAndMonth(userId, request.categoryId(), request.month())) {
            throw duplicate();
        }

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategoryId(request.categoryId());
        budget.setMonth(request.month());
        budget.setLimitAmount(request.limitAmount());
        budgetRepository.save(budget);
        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse update(Long userId, Long id, BudgetRequest request) {
        owners.acquire(userId);
        Budget budget = findOwn(userId, id);
        checkCategory(userId, request.categoryId());
        if (budgetRepository.existsByUserIdAndCategoryIdAndMonthAndIdNot(userId, request.categoryId(),
                request.month(), id)) {
            throw duplicate();
        }

        budget.setCategoryId(request.categoryId());
        budget.setMonth(request.month());
        budget.setLimitAmount(request.limitAmount());
        budgetRepository.save(budget);
        return toResponse(budget);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        owners.acquire(userId);
        budgetRepository.delete(findOwn(userId, id));
    }

    /** The category must be a default or the user's own, and an EXPENSE category (no budgets on income). */
    private void checkCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findVisibleById(categoryId, userId)
                .orElseThrow(() -> ApiException.badRequest("Category not found"));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw ApiException.badRequest("Budgets can only be set for expense categories");
        }
    }

    // Another user's budget gives the same 404 as a missing one (never 403)
    private Budget findOwn(Long userId, Long id) {
        return budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
    }

    private ApiException duplicate() {
        return new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT,
                "There is already a budget for this category in this month");
    }

    private BudgetResponse toResponse(Budget budget) {
        // From the 1st to the last day of the budget month (28, 29, 30 or 31)
        YearMonth month = YearMonth.parse(budget.getMonth());
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();

        BigDecimal used = transactionRepository
                .sumExpenseForCategory(budget.getUserId(), budget.getCategoryId(), firstDay, lastDay)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal limit = budget.getLimitAmount();
        BigDecimal remaining = limit.subtract(used);

        String state;
        if (used.compareTo(limit) > 0) {
            state = "OVER";
        } else if (used.multiply(HUNDRED).compareTo(limit.multiply(NEAR_PERCENT)) >= 0) {
            // used / limit >= 80%, written without division so there is no rounding
            state = "NEAR";
        } else {
            state = "OK";
        }

        return new BudgetResponse(budget.getId(), budget.getCategoryId(), budget.getMonth(), limit, used,
                remaining, state);
    }
}
