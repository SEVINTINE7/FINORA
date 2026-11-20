package lk.finora.api.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.bill.Bill;
import lk.finora.api.bill.BillRepository;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryKind;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.category.NeedOrWant;
import lk.finora.api.transaction.Transaction;
import lk.finora.api.transaction.TransactionRepository;
import lk.finora.api.transaction.TransactionType;

/**
 * Deterministic spending metrics (T21): plain sums of the user's own records, no AI and no guessing.
 * Thresholds and windows follow D10; savings and refunds follow D18.
 */
@Service
public class MetricsService {

    // Name of the default expense category that means "money put aside" (V6 seed)
    static final String SAVINGS_CATEGORY = "Savings";
    // D10: payday window = the income day and the next 2 days
    static final int PAYDAY_EXTRA_DAYS = 2;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BillRepository billRepository;

    public MetricsService(TransactionRepository transactionRepository, CategoryRepository categoryRepository,
            BillRepository billRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.billRepository = billRepository;
    }

    @Transactional(readOnly = true)
    public MonthMetrics forMonth(Long userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        List<Transaction> records = transactionRepository.findByUserIdAndTxnDateBetween(userId, from, to);
        Map<Long, Category> categories = loadCategories(records);

        // Split the records into the groups we need. A record can be in more than one group
        // (e.g. a want bought on a weekend), but never twice in the same group.
        List<Transaction> incomes = new ArrayList<>();
        List<Transaction> expenses = new ArrayList<>();
        List<Transaction> needs = new ArrayList<>();
        List<Transaction> wants = new ArrayList<>();
        List<Transaction> unclassified = new ArrayList<>();
        List<Transaction> savedToCategory = new ArrayList<>();
        List<Transaction> weekend = new ArrayList<>();
        List<Transaction> weekday = new ArrayList<>();

        for (Transaction t : records) {
            if (t.getType() == TransactionType.INCOME) {
                incomes.add(t);
                continue;
            }
            expenses.add(t);

            Category category = categories.get(t.getCategoryId()); // null when uncategorised
            if (category != null && category.getNeedOrWant() == NeedOrWant.NEED) {
                needs.add(t);
            } else if (category != null && category.getNeedOrWant() == NeedOrWant.WANT) {
                wants.add(t);
            } else {
                unclassified.add(t);
            }
            if (category != null && isSavingsCategory(category)) {
                savedToCategory.add(t);
            }

            if (isWeekend(t.getTxnDate())) {
                weekend.add(t);
            } else {
                weekday.add(t);
            }
        }

        // Payday: collect the window days first, so overlapping windows count each expense once
        Set<LocalDate> paydayDays = new HashSet<>();
        for (Transaction income : incomes) {
            for (int i = 0; i <= PAYDAY_EXTRA_DAYS; i++) {
                paydayDays.add(income.getTxnDate().plusDays(i));
            }
        }
        List<Transaction> payday = new ArrayList<>();
        for (Transaction expense : expenses) {
            if (paydayDays.contains(expense.getTxnDate())) {
                payday.add(expense);
            }
        }

        Metric income = metric(incomes, from, to);
        Metric expense = metric(expenses, from, to);
        Metric wantsMetric = metric(wants, from, to);
        Metric fixedBills = fixedBills(userId, from, to);
        // leftOver uses every record of the month
        Metric leftOver = new Metric(income.amount().subtract(expense.amount()), from, to, records.size());

        return new MonthMetrics(
                month.toString(),
                income,
                expense,
                metric(needs, from, to),
                wantsMetric,
                metric(unclassified, from, to),
                fixedBills,
                leftOver,
                metric(savedToCategory, from, to),
                metric(payday, from, to),
                metric(weekend, from, to),
                metric(weekday, from, to),
                ratio(wantsMetric.amount(), income.amount()),
                ratio(fixedBills.amount(), income.amount()),
                ratio(leftOver.amount(), income.amount()));
    }

    // ---------- helpers ----------

    // Sum of the records, with the period and the number of records it came from (T21.2)
    private Metric metric(List<Transaction> records, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (Transaction t : records) {
            sum = sum.add(t.getAmount());
        }
        return new Metric(sum, from, to, records.size());
    }

    // Planned monthly bills (T18): every active bill counts once per month
    private Metric fixedBills(Long userId, LocalDate from, LocalDate to) {
        List<Bill> bills = billRepository.findByUserIdAndActiveTrueOrderByDueDayAscIdAsc(userId);
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (Bill bill : bills) {
            sum = sum.add(bill.getAmount());
        }
        return new Metric(sum, from, to, bills.size());
    }

    /** part / income with 4 decimals, or null when there is no income (never divide by zero). */
    static BigDecimal ratio(BigDecimal part, BigDecimal income) {
        if (income.signum() == 0) {
            return null;
        }
        return part.divide(income, 4, RoundingMode.HALF_UP);
    }

    static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isSavingsCategory(Category category) {
        return category.getKind() == CategoryKind.EXPENSE && SAVINGS_CATEGORY.equals(category.getName());
    }

    // Loads only the categories these records use (also archived ones, so old records still count)
    private Map<Long, Category> loadCategories(List<Transaction> records) {
        Set<Long> ids = new HashSet<>();
        for (Transaction t : records) {
            if (t.getCategoryId() != null) {
                ids.add(t.getCategoryId());
            }
        }
        Map<Long, Category> result = new HashMap<>();
        for (Category category : categoryRepository.findAllById(ids)) {
            result.put(category.getId(), category);
        }
        return result;
    }
}
