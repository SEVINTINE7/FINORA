package lk.finora.api.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
 * T21: unit tests with hand-calculated numbers (no database; the repositories are Mockito mocks).
 * November 2026 calendar used below: 2 Mon, 5 Thu, 6 Fri, 7 Sat, 15 Sun, 25 Wed, 26 Thu, 27 Fri, 28 Sat.
 */
class MetricsServiceTest {

    private static final Long USER = 1L;
    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    // Categories like the V6 defaults
    private static final Category SALARY = category(10L, "Salary", CategoryKind.INCOME, null);
    private static final Category OTHER_INCOME = category(11L, "Other", CategoryKind.INCOME, null);
    private static final Category FOOD = category(20L, "Food", CategoryKind.EXPENSE, NeedOrWant.NEED);
    private static final Category SHOPPING = category(21L, "Shopping", CategoryKind.EXPENSE, NeedOrWant.WANT);
    private static final Category SAVINGS = category(22L, "Savings", CategoryKind.EXPENSE, null);

    static {
        ReflectionTestUtils.setField(SAVINGS, "semanticCode", "SAVINGS");
    }
    private static final Category RENT = category(23L, "Rent", CategoryKind.EXPENSE, NeedOrWant.NEED);

    private TransactionRepository transactionRepository;
    private CategoryRepository categoryRepository;
    private BillRepository billRepository;
    private MetricsService service;

    private final List<Transaction> records = new ArrayList<>();
    private final List<Bill> bills = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        billRepository = mock(BillRepository.class);
        service = new MetricsService(transactionRepository, categoryRepository, billRepository);

        when(transactionRepository.findByUserIdAndTxnDateBetween(USER, FROM, TO)).thenReturn(records);
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(SALARY, OTHER_INCOME, FOOD, SHOPPING, SAVINGS, RENT));
        when(billRepository.findByUserIdAndActiveTrueOrderByDueDayAscIdAsc(USER)).thenReturn(bills);
    }

    private static Category category(Long id, String name, CategoryKind kind, NeedOrWant needOrWant) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id); // id is normally set by MySQL
        category.setName(name);
        category.setKind(kind);
        category.setNeedOrWant(needOrWant);
        return category;
    }

    private void income(String amount, int day, Category category) {
        add(TransactionType.INCOME, amount, day, category);
    }

    private void expense(String amount, int day, Category category) {
        add(TransactionType.EXPENSE, amount, day, category);
    }

    private void add(TransactionType type, String amount, int day, Category category) {
        Transaction t = new Transaction();
        t.setUserId(USER);
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setTxnDate(LocalDate.of(2026, 11, day));
        t.setCategoryId(category == null ? null : category.getId());
        records.add(t);
    }

    private void bill(String amount) {
        Bill bill = new Bill();
        bill.setAmount(new BigDecimal(amount));
        bills.add(bill);
    }

    // Checks amount (exact, incl. 2 decimals), period and record count
    private void check(Metric metric, String amount, int count) {
        assertThat(metric.amount().toPlainString()).isEqualTo(amount);
        assertThat(metric.count()).isEqualTo(count);
        assertThat(metric.from()).isEqualTo(FROM);
        assertThat(metric.to()).isEqualTo(TO);
    }

    @Test
    void fullMonthHandCalculated() {
        income("60000.00", 25, SALARY);       // payday window 25, 26, 27
        income("1500.00", 5, OTHER_INCOME);   // a refund (D18), payday window 5, 6, 7
        expense("20000.00", 2, RENT);         // need, weekday
        expense("1250.75", 6, FOOD);          // need, weekday, payday
        expense("4999.99", 7, SHOPPING);      // want, weekend, payday
        expense("500.00", 15, FOOD);          // need, weekend
        expense("3000.00", 26, SHOPPING);     // want, weekday, payday
        expense("10000.00", 27, SAVINGS);     // unclassified, saved, weekday, payday
        expense("99.26", 28, null);           // uncategorised -> unclassified, weekend
        bill("20000.00");
        bill("1499.99");

        MonthMetrics m = service.forMonth(USER, NOV);

        assertThat(m.month()).isEqualTo("2026-11");
        check(m.income(), "61500.00", 2);           // 60000.00 + 1500.00
        check(m.expense(), "39850.00", 7);          // all 7 expenses
        check(m.needs(), "21750.75", 3);            // 20000.00 + 1250.75 + 500.00
        check(m.wants(), "7999.99", 2);             // 4999.99 + 3000.00
        check(m.unclassified(), "10099.26", 2);     // 10000.00 + 99.26
        check(m.fixedBills(), "21499.99", 2);       // 20000.00 + 1499.99
        check(m.leftOver(), "21650.00", 9);         // 61500.00 - 39850.00, from all 9 records
        check(m.savedToCategory(), "10000.00", 1);
        check(m.paydaySpend(), "19250.74", 4);      // 1250.75 + 4999.99 + 3000.00 + 10000.00
        check(m.weekendSpend(), "5599.25", 3);      // 4999.99 + 500.00 + 99.26
        check(m.weekdaySpend(), "34250.75", 4);     // 20000.00 + 1250.75 + 3000.00 + 10000.00

        assertThat(m.wantsRatio()).isEqualTo(new BigDecimal("0.1301"));      // 7999.99 / 61500.00
        assertThat(m.fixedBillsRatio()).isEqualTo(new BigDecimal("0.3496")); // 21499.99 / 61500.00
        assertThat(m.savingsRate()).isEqualTo(new BigDecimal("0.3520"));     // 21650.00 / 61500.00

        // The groups add up to the expense total
        BigDecimal byNeed = m.needs().amount().add(m.wants().amount()).add(m.unclassified().amount());
        assertThat(byNeed).isEqualTo(m.expense().amount());
        assertThat(m.weekendSpend().amount().add(m.weekdaySpend().amount())).isEqualTo(m.expense().amount());
    }

    @Test
    void noIncomeGivesNullRatiosNotDivideByZero() {
        expense("2500.50", 2, SHOPPING);
        bill("1000.00");

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.income(), "0.00", 0);
        check(m.leftOver(), "-2500.50", 1);
        check(m.paydaySpend(), "0.00", 0); // no income = no payday
        assertThat(m.wantsRatio()).isNull();
        assertThat(m.fixedBillsRatio()).isNull();
        assertThat(m.savingsRate()).isNull();
    }

    @Test
    void emptyMonthIsAllZeros() {
        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.income(), "0.00", 0);
        check(m.expense(), "0.00", 0);
        check(m.needs(), "0.00", 0);
        check(m.fixedBills(), "0.00", 0);
        check(m.leftOver(), "0.00", 0);
        assertThat(m.savingsRate()).isNull();
    }

    @Test
    void overlappingPaydayWindowsCountEachExpenseOnce() {
        income("30000.00", 2, SALARY); // window 2, 3, 4
        income("5000.00", 3, SALARY);  // window 3, 4, 5
        expense("100.00", 4, FOOD);    // in both windows -> counted once
        expense("200.00", 5, FOOD);    // last day of the second window
        expense("400.00", 6, FOOD);    // outside both

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.paydaySpend(), "300.00", 2);
    }

    @Test
    void refundIsIncomeInOtherCategory() {
        // D18: no refund type; a refund of a 2000.00 purchase is entered as INCOME in "Other"
        expense("2000.00", 6, SHOPPING);
        income("2000.00", 9, OTHER_INCOME);

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.income(), "2000.00", 1);
        check(m.wants(), "2000.00", 1); // the purchase is still a want
        check(m.leftOver(), "0.00", 2); // but the money came back
        assertThat(m.wantsRatio()).isEqualTo(new BigDecimal("1.0000"));
    }

    @Test
    void savingsAllocationIsAlreadyAnExpenseNotAnExtraDeduction() {
        income("1000.00", 2, SALARY);
        expense("200.00", 3, SAVINGS);
        expense("900.00", 6, FOOD);

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.expense(), "1100.00", 2);
        check(m.savedToCategory(), "200.00", 1);
        check(m.unclassified(), "200.00", 1);
        check(m.leftOver(), "-100.00", 3); // 1000 - 200 - 900, not -300
        assertThat(m.savingsRate()).isEqualTo(new BigDecimal("-0.1000"));
    }

    @Test
    void missingCategoryIsUnclassifiedWithoutLosingTheExpense() {
        Category missing = category(99L, "Deleted category", CategoryKind.EXPENSE, NeedOrWant.WANT);
        expense("12.34", 7, missing); // repository does not return category 99
        expense("0.66", 9, null);

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.expense(), "13.00", 2);
        check(m.unclassified(), "13.00", 2);
        check(m.wants(), "0.00", 0);
        check(m.needs(), "0.00", 0);
        check(m.savedToCategory(), "0.00", 0);
    }

    @Test
    void leapFebruaryUsesInclusiveMonthBoundsAndLastDayPayday() {
        YearMonth feb = YearMonth.of(2028, 2);
        LocalDate first = feb.atDay(1);
        LocalDate last = feb.atEndOfMonth();
        Transaction salary = new Transaction();
        salary.setUserId(USER);
        salary.setType(TransactionType.INCOME);
        salary.setAmount(new BigDecimal("100.00"));
        salary.setTxnDate(last);
        salary.setCategoryId(SALARY.getId());
        Transaction purchase = new Transaction();
        purchase.setUserId(USER);
        purchase.setType(TransactionType.EXPENSE);
        purchase.setAmount(new BigDecimal("1.25"));
        purchase.setTxnDate(last);
        purchase.setCategoryId(FOOD.getId());
        when(transactionRepository.findByUserIdAndTxnDateBetween(USER, first, last))
                .thenReturn(List.of(salary, purchase));

        MonthMetrics m = service.forMonth(USER, feb);

        assertThat(m.month()).isEqualTo("2028-02");
        assertThat(m.expense()).isEqualTo(new Metric(new BigDecimal("1.25"), first, last, 1));
        assertThat(m.paydaySpend()).isEqualTo(new Metric(new BigDecimal("1.25"), first, last, 1));
        assertThat(m.leftOver()).isEqualTo(new Metric(new BigDecimal("98.75"), first, last, 2));
    }

    @Test
    void previousMonthIncomeExtendsPaydayWindowWithoutChangingMonthlyTotals() {
        Transaction priorIncome = new Transaction();
        priorIncome.setUserId(USER);
        priorIncome.setType(TransactionType.INCOME);
        priorIncome.setAmount(new BigDecimal("50000.00"));
        priorIncome.setTxnDate(FROM.minusDays(1));
        when(transactionRepository.findByUserIdAndTxnDateBetween(USER, FROM.minusDays(2), FROM.minusDays(1)))
                .thenReturn(List.of(priorIncome));
        expense("100.00", 1, FOOD);
        expense("200.00", 2, FOOD);
        expense("400.00", 3, FOOD); // outside October 31 + two days

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.paydaySpend(), "300.00", 2);
        check(m.expense(), "700.00", 3);
        check(m.income(), "0.00", 0); // prior income is context, not November income
        check(m.leftOver(), "-700.00", 3);
        assertThat(m.savingsRate()).isNull();
    }

    @Test
    void crossYearPaydayWindowsOverlapWithoutDoubleCounting() {
        YearMonth january = YearMonth.of(2027, 1);
        LocalDate first = january.atDay(1);
        LocalDate last = january.atEndOfMonth();
        Transaction priorIncome = new Transaction();
        priorIncome.setType(TransactionType.INCOME);
        priorIncome.setTxnDate(first.minusDays(1));
        priorIncome.setAmount(new BigDecimal("1000.00"));
        Transaction currentIncome = new Transaction();
        currentIncome.setType(TransactionType.INCOME);
        currentIncome.setTxnDate(first);
        currentIncome.setAmount(new BigDecimal("200.00"));
        Transaction purchase = new Transaction();
        purchase.setType(TransactionType.EXPENSE);
        purchase.setTxnDate(first.plusDays(1));
        purchase.setAmount(new BigDecimal("25.00"));
        purchase.setCategoryId(SHOPPING.getId());
        bill("80.00");
        when(transactionRepository.findByUserIdAndTxnDateBetween(USER, first, last))
                .thenReturn(List.of(currentIncome, purchase));
        when(transactionRepository.findByUserIdAndTxnDateBetween(USER, first.minusDays(2), first.minusDays(1)))
                .thenReturn(List.of(priorIncome));

        MonthMetrics m = service.forMonth(USER, january);

        assertThat(m.paydaySpend()).isEqualTo(new Metric(new BigDecimal("25.00"), first, last, 1));
        assertThat(m.income()).isEqualTo(new Metric(new BigDecimal("200.00"), first, last, 1));
        assertThat(m.leftOver()).isEqualTo(new Metric(new BigDecimal("175.00"), first, last, 2));
        assertThat(m.wants()).isEqualTo(new Metric(new BigDecimal("25.00"), first, last, 1));
        assertThat(m.fixedBills()).isEqualTo(new Metric(new BigDecimal("80.00"), first, last, 1));
        assertThat(m.wantsRatio()).isEqualTo(new BigDecimal("0.1250"));
        assertThat(m.fixedBillsRatio()).isEqualTo(new BigDecimal("0.4000"));
        assertThat(m.savingsRate()).isEqualTo(new BigDecimal("0.8750"));
    }

    @Test
    void customSavingsNameDoesNotMasqueradeAsDefaultSavingsCategory() {
        Category custom = category(90L, "Savings", CategoryKind.EXPENSE, NeedOrWant.WANT);
        custom.setUserId(USER);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(SAVINGS, custom));
        expense("100.00", 6, SAVINGS);
        expense("900.00", 6, custom);

        MonthMetrics m = service.forMonth(USER, NOV);

        check(m.savedToCategory(), "100.00", 1);
        check(m.expense(), "1000.00", 2);
        check(m.wants(), "900.00", 1);
        check(m.unclassified(), "100.00", 1);
    }

    @Test
    void ratioAndWeekendHelpers() {
        assertThat(MetricsService.ratio(new BigDecimal("1.00"), new BigDecimal("3.00")))
                .isEqualTo(new BigDecimal("0.3333"));
        assertThat(MetricsService.ratio(new BigDecimal("2.00"), new BigDecimal("3.00")))
                .isEqualTo(new BigDecimal("0.6667")); // HALF_UP
        assertThat(MetricsService.ratio(new BigDecimal("5.00"), BigDecimal.ZERO)).isNull();
        assertThat(MetricsService.isWeekend(LocalDate.of(2026, 11, 7))).isTrue();  // Saturday
        assertThat(MetricsService.isWeekend(LocalDate.of(2026, 11, 8))).isTrue();  // Sunday
        assertThat(MetricsService.isWeekend(LocalDate.of(2026, 11, 9))).isFalse(); // Monday
    }
}
