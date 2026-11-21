package lk.finora.api.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T22: pattern rules on hand-made November 2026 metrics.
 * November 2026 has 30 days: 9 weekend days (Sat 7, 14, 21, 28; Sun 1, 8, 15, 22, 29) and 21 weekdays.
 */
class PatternRulesTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    private static Metric m(String amount) {
        return new Metric(new BigDecimal(amount), FROM, TO, 1);
    }

    /** Builds metrics with only the numbers the rules read. Unused groups are 0.00. */
    private static MonthMetrics metrics(String income, String expense, String wants, String bills, String payday,
            String weekend, String weekday) {
        BigDecimal in = new BigDecimal(income);
        BigDecimal leftOver = in.subtract(new BigDecimal(expense));
        return new MonthMetrics("2026-11", m(income), m(expense), m("0.00"), m(wants), m("0.00"), m(bills),
                new Metric(leftOver, FROM, TO, 2), m("0.00"), m(payday), m(weekend), m(weekday),
                MetricsService.ratio(new BigDecimal(wants), in),
                MetricsService.ratio(new BigDecimal(bills), in),
                MetricsService.ratio(leftOver, in));
    }

    private static List<String> types(MonthMetrics metrics) {
        List<String> types = new ArrayList<>();
        for (SpendingPattern p : PatternRules.detect(metrics)) {
            types.add(p.type());
        }
        return types;
    }

    // ---------- high wants (D10: wants > 40% of income) ----------

    @Test
    void highWants() {
        // income 10000.00, so the line is 4000.00
        assertThat(types(metrics("10000.00", "6000.00", "6000.00", "0.00", "0.00", "0.00", "6000.00")))
                .containsExactly(PatternRules.HIGH_WANTS);                                       // normal: 60%
        assertThat(types(metrics("10000.00", "4000.00", "4000.00", "0.00", "0.00", "0.00", "4000.00")))
                .doesNotContain(PatternRules.HIGH_WANTS);                                        // exactly 40%
        assertThat(types(metrics("10000.00", "3999.99", "3999.99", "0.00", "0.00", "0.00", "3999.99")))
                .doesNotContain(PatternRules.HIGH_WANTS);                                        // just below
        assertThat(types(metrics("10000.00", "4000.01", "4000.01", "0.00", "0.00", "0.00", "4000.01")))
                .contains(PatternRules.HIGH_WANTS);                                              // one cent above
    }

    @Test
    void highWantsFacts() {
        SpendingPattern p = PatternRules.detect(
                metrics("10000.00", "6000.00", "6000.00", "0.00", "0.00", "0.00", "6000.00")).get(0);

        assertThat(p.facts().get("wants")).isEqualTo(new BigDecimal("6000.00"));
        assertThat(p.facts().get("income")).isEqualTo(new BigDecimal("10000.00"));
        assertThat(p.facts().get("wantsRatio")).isEqualTo(new BigDecimal("0.6000"));
        assertThat(p.facts().get("threshold")).isEqualTo(new BigDecimal("0.40"));
        assertThat(p.facts().get("wantsCount")).isEqualTo(1);
    }

    // ---------- high fixed bills (D10: bills > 50% of income) ----------

    @Test
    void highFixedBills() {
        assertThat(types(metrics("10000.00", "0.00", "0.00", "7000.00", "0.00", "0.00", "0.00")))
                .containsExactly(PatternRules.HIGH_FIXED_BILLS);
        assertThat(types(metrics("10000.00", "0.00", "0.00", "5000.00", "0.00", "0.00", "0.00"))).isEmpty();
        assertThat(types(metrics("10000.00", "0.00", "0.00", "4999.99", "0.00", "0.00", "0.00"))).isEmpty();
        assertThat(types(metrics("10000.00", "0.00", "0.00", "5000.01", "0.00", "0.00", "0.00")))
                .containsExactly(PatternRules.HIGH_FIXED_BILLS);
    }

    // ---------- payday spending (D19: > 30% of the month's spending) ----------

    @Test
    void paydaySpending() {
        // expense 10000.00, so the line is 3000.00. All spending on weekdays, so the weekend rule stays quiet.
        assertThat(types(metrics("20000.00", "10000.00", "0.00", "0.00", "5000.00", "0.00", "10000.00")))
                .containsExactly(PatternRules.PAYDAY_SPENDING);
        assertThat(types(metrics("20000.00", "10000.00", "0.00", "0.00", "3000.00", "0.00", "10000.00"))).isEmpty();
        assertThat(types(metrics("20000.00", "10000.00", "0.00", "0.00", "2999.99", "0.00", "10000.00"))).isEmpty();
        assertThat(types(metrics("20000.00", "10000.00", "0.00", "0.00", "3000.01", "0.00", "10000.00")))
                .containsExactly(PatternRules.PAYDAY_SPENDING);

        SpendingPattern p = PatternRules.detect(
                metrics("20000.00", "10000.00", "0.00", "0.00", "5000.00", "0.00", "10000.00")).get(0);
        assertThat(p.facts().get("paydayShare")).isEqualTo(new BigDecimal("0.5000"));
    }

    // ---------- weekend spending (D19: weekend day average > 1.5 x weekday day average) ----------

    @Test
    void weekendSpending() {
        // weekday 2100.00 over 21 days = 100.00 a day, so the line is 150.00 a weekend day = 1350.00 over 9 days
        assertThat(types(metrics("0.00", "4800.00", "0.00", "0.00", "0.00", "2700.00", "2100.00")))
                .containsExactly(PatternRules.WEEKEND_SPENDING);                                 // 300.00 a day
        assertThat(types(metrics("0.00", "3450.00", "0.00", "0.00", "0.00", "1350.00", "2100.00"))).isEmpty();
        assertThat(types(metrics("0.00", "3449.99", "0.00", "0.00", "0.00", "1349.99", "2100.00"))).isEmpty();
        assertThat(types(metrics("0.00", "3450.01", "0.00", "0.00", "0.00", "1350.01", "2100.00")))
                .containsExactly(PatternRules.WEEKEND_SPENDING);

        SpendingPattern p = PatternRules.detect(
                metrics("0.00", "4800.00", "0.00", "0.00", "0.00", "2700.00", "2100.00")).get(0);
        assertThat(p.facts().get("weekendDays")).isEqualTo(9);
        assertThat(p.facts().get("weekdayDays")).isEqualTo(21);
        assertThat(p.facts().get("weekendDailyAverage")).isEqualTo(new BigDecimal("300.00"));
        assertThat(p.facts().get("weekdayDailyAverage")).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void onlyWeekendSpendingIsAPattern() {
        // No weekday spending at all: any weekend spending is above 1.5 x 0
        assertThat(types(metrics("0.00", "0.01", "0.00", "0.00", "0.00", "0.01", "0.00")))
                .containsExactly(PatternRules.WEEKEND_SPENDING);
    }

    // ---------- extreme and empty months ----------

    @Test
    void extremeMonthGivesAllFour() {
        // Wants and bills far above income, everything spent on payday weekends
        assertThat(types(metrics("1000.00", "900000.00", "800000.00", "999999.99", "900000.00", "900000.00", "0.00")))
                .containsExactly(PatternRules.HIGH_WANTS, PatternRules.HIGH_FIXED_BILLS,
                        PatternRules.PAYDAY_SPENDING, PatternRules.WEEKEND_SPENDING);
    }

    @Test
    void noIncomeSkipsIncomeRules() {
        // Huge wants and bills, but no income: ratios are null, so no HIGH_WANTS / HIGH_FIXED_BILLS
        assertThat(types(metrics("0.00", "5000.00", "5000.00", "8000.00", "0.00", "0.00", "5000.00"))).isEmpty();
    }

    @Test
    void emptyMonthGivesNothing() {
        assertThat(types(metrics("0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"))).isEmpty();
    }

    @Test
    void roundedIncomeRatiosDoNotHideOneCentThresholdBreaches() {
        // Both displayed ratios round back to the thresholds, but exact amounts are above them.
        List<SpendingPattern> patterns = PatternRules.detect(
                metrics("10000.00", "4000.01", "4000.01", "5000.01", "0.00", "0.00", "4000.01"));

        assertThat(patterns).extracting(SpendingPattern::type)
                .containsExactly(PatternRules.HIGH_WANTS, PatternRules.HIGH_FIXED_BILLS);
        assertThat(patterns.get(0).facts().get("wantsRatio")).isEqualTo(new BigDecimal("0.4000"));
        assertThat(patterns.get(1).facts())
                .containsEntry("fixedBills", new BigDecimal("5000.01"))
                .containsEntry("billCount", 1)
                .containsEntry("income", new BigDecimal("10000.00"))
                .containsEntry("fixedBillsRatio", new BigDecimal("0.5000"))
                .containsEntry("threshold", new BigDecimal("0.50"));
    }

    @Test
    void roundedPaydayShareDoesNotHideOneCentThresholdBreach() {
        SpendingPattern pattern = PatternRules.detect(
                metrics("20000.00", "10000.00", "0.00", "0.00", "3000.01", "0.00", "10000.00")).get(0);

        assertThat(pattern.type()).isEqualTo(PatternRules.PAYDAY_SPENDING);
        assertThat(pattern.facts())
                .containsEntry("paydaySpend", new BigDecimal("3000.01"))
                .containsEntry("paydayCount", 1)
                .containsEntry("expense", new BigDecimal("10000.00"))
                .containsEntry("paydayShare", new BigDecimal("0.3000"))
                .containsEntry("threshold", new BigDecimal("0.30"));
    }

    @Test
    void leapFebruaryWeekendThresholdUsesThatMonthsCalendar() {
        // February 2028: 8 weekend days, 21 weekdays; 1200 / 8 = 1.5 * (2100 / 21).
        LocalDate first = LocalDate.of(2028, 2, 1);
        LocalDate last = LocalDate.of(2028, 2, 29);
        Metric zero = new Metric(new BigDecimal("0.00"), first, last, 0);
        for (String weekend : List.of("1200.00", "1200.01")) {
            Metric expense = new Metric(new BigDecimal(weekend).add(new BigDecimal("2100.00")), first, last, 2);
            MonthMetrics month = new MonthMetrics("2028-02", zero, expense, zero, zero, zero, zero,
                    zero, zero, zero, new Metric(new BigDecimal(weekend), first, last, 1),
                    new Metric(new BigDecimal("2100.00"), first, last, 1), null, null, null);

            if (weekend.equals("1200.00")) {
                assertThat(PatternRules.detect(month)).isEmpty();
            } else {
                assertThat(types(month)).containsExactly(PatternRules.WEEKEND_SPENDING);
                assertThat(PatternRules.detect(month).get(0).facts())
                        .containsEntry("weekendDays", 8)
                        .containsEntry("weekdayDays", 21);
            }
        }
    }

    @Test
    void dayCounting() {
        assertThat(PatternRules.countDays(FROM, TO)).isEqualTo(30);
        assertThat(PatternRules.countWeekendDays(FROM, TO)).isEqualTo(9);
        // February 2026: 28 days, 8 weekend days
        assertThat(PatternRules.countWeekendDays(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).isEqualTo(8);
    }
}
