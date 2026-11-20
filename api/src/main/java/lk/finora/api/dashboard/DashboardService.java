package lk.finora.api.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.budget.dto.BudgetRequest;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.dashboard.dto.CategoryTotal;
import lk.finora.api.dashboard.dto.DashboardResponse;
import lk.finora.api.dashboard.dto.MonthTotal;
import lk.finora.api.transaction.TransactionRepository;
import lk.finora.api.transaction.TransactionType;

/** Totals for the dashboard screen (T20). All sums are done by MySQL on DECIMAL values. */
@Service
public class DashboardService {

    // Our users are in Sri Lanka, so "this month" is the month in Colombo
    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    private static final int TREND_MONTHS = 6;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public DashboardService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /** month is YYYY-MM; null or empty means the current month. */
    @Transactional(readOnly = true)
    public DashboardResponse get(Long userId, String month) {
        YearMonth yearMonth = parseMonth(month);
        MonthTotal totals = monthTotal(userId, yearMonth);

        // Trend: the 5 months before + the chosen month, oldest first
        List<MonthTotal> trend = new ArrayList<>();
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            trend.add(monthTotal(userId, yearMonth.minusMonths(i)));
        }

        return new DashboardResponse(yearMonth.toString(), totals.income(), totals.expense(), totals.net(),
                byCategory(userId, yearMonth), trend);
    }

    private MonthTotal monthTotal(Long userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = scale(transactionRepository.sumAmount(userId, TransactionType.INCOME, from, to));
        BigDecimal expense = scale(transactionRepository.sumAmount(userId, TransactionType.EXPENSE, from, to));
        return new MonthTotal(month.toString(), income, expense, income.subtract(expense));
    }

    // Expenses only, biggest first. Records without a category come back as one item with categoryId null.
    private List<CategoryTotal> byCategory(Long userId, YearMonth month) {
        List<CategoryTotal> result = new ArrayList<>();
        List<Object[]> rows = transactionRepository.sumExpenseByCategory(userId, month.atDay(1), month.atEndOfMonth());
        for (Object[] row : rows) {
            Long categoryId = (Long) row[0];
            BigDecimal total = scale((BigDecimal) row[1]);
            String name = null;
            String nameSi = null;
            if (categoryId != null) {
                Category category = categoryRepository.findById(categoryId).orElse(null);
                if (category != null) {
                    name = category.getName();
                    nameSi = category.getNameSi();
                }
            }
            result.add(new CategoryTotal(categoryId, name, nameSi, total));
        }
        return result;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(SRI_LANKA);
        }
        if (!month.matches(BudgetRequest.MONTH_PATTERN)) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        return YearMonth.parse(month);
    }

    // 2 decimals, so an empty month shows 0.00 and not 0
    private BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
