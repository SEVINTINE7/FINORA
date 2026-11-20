package lk.finora.api.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spending-pattern rules (T22.1). Thresholds from D10 and D19. Every rule uses "more than", so a month
 * exactly at the threshold gives no insight. Comparisons are done by multiplying (no division), so
 * rounding can never change the result; the rounded ratios in facts are only for showing.
 */
public final class PatternRules {

    public static final String HIGH_WANTS = "HIGH_WANTS";
    public static final String HIGH_FIXED_BILLS = "HIGH_FIXED_BILLS";
    public static final String PAYDAY_SPENDING = "PAYDAY_SPENDING";
    public static final String WEEKEND_SPENDING = "WEEKEND_SPENDING";

    static final BigDecimal WANTS_LIMIT = new BigDecimal("0.40");   // D10: wants > 40% of income
    static final BigDecimal BILLS_LIMIT = new BigDecimal("0.50");   // D10: bills > 50% of income
    static final BigDecimal PAYDAY_LIMIT = new BigDecimal("0.30");  // D19: > 30% of the month's spending
    static final BigDecimal WEEKEND_FACTOR = new BigDecimal("1.5"); // D19: weekend day avg > 1.5 x weekday avg

    private PatternRules() {
    }

    public static List<SpendingPattern> detect(MonthMetrics m) {
        List<SpendingPattern> result = new ArrayList<>();
        BigDecimal income = m.income().amount();
        BigDecimal expense = m.expense().amount();

        // Rules that compare with income need some income (T21.3: no income = no ratio)
        if (income.signum() > 0) {
            // wants > 0.40 x income
            if (m.wants().amount().compareTo(WANTS_LIMIT.multiply(income)) > 0) {
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("wants", m.wants().amount());
                facts.put("wantsCount", m.wants().count());
                facts.put("income", income);
                facts.put("wantsRatio", m.wantsRatio());
                facts.put("threshold", WANTS_LIMIT);
                result.add(new SpendingPattern(HIGH_WANTS, facts));
            }
            // bills > 0.50 x income
            if (m.fixedBills().amount().compareTo(BILLS_LIMIT.multiply(income)) > 0) {
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("fixedBills", m.fixedBills().amount());
                facts.put("billCount", m.fixedBills().count());
                facts.put("income", income);
                facts.put("fixedBillsRatio", m.fixedBillsRatio());
                facts.put("threshold", BILLS_LIMIT);
                result.add(new SpendingPattern(HIGH_FIXED_BILLS, facts));
            }
        }

        // Rules that compare with spending need some spending
        if (expense.signum() > 0) {
            // payday spend > 0.30 x expense
            if (m.paydaySpend().amount().compareTo(PAYDAY_LIMIT.multiply(expense)) > 0) {
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("paydaySpend", m.paydaySpend().amount());
                facts.put("paydayCount", m.paydaySpend().count());
                facts.put("expense", expense);
                facts.put("paydayShare", m.paydaySpend().amount().divide(expense, 4, RoundingMode.HALF_UP));
                facts.put("threshold", PAYDAY_LIMIT);
                result.add(new SpendingPattern(PAYDAY_SPENDING, facts));
            }

            // weekendSpend / weekendDays > 1.5 x weekdaySpend / weekdayDays
            // is the same as: weekendSpend x weekdayDays > 1.5 x weekdaySpend x weekendDays
            int weekendDays = countWeekendDays(m.weekendSpend().from(), m.weekendSpend().to());
            int weekdayDays = countDays(m.weekendSpend().from(), m.weekendSpend().to()) - weekendDays;
            BigDecimal weekendSpend = m.weekendSpend().amount();
            BigDecimal weekdaySpend = m.weekdaySpend().amount();
            BigDecimal left = weekendSpend.multiply(BigDecimal.valueOf(weekdayDays));
            BigDecimal right = WEEKEND_FACTOR.multiply(weekdaySpend).multiply(BigDecimal.valueOf(weekendDays));
            if (left.compareTo(right) > 0) {
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("weekendSpend", weekendSpend);
                facts.put("weekendCount", m.weekendSpend().count());
                facts.put("weekendDays", weekendDays);
                facts.put("weekendDailyAverage", weekendSpend.divide(BigDecimal.valueOf(weekendDays), 2,
                        RoundingMode.HALF_UP));
                facts.put("weekdaySpend", weekdaySpend);
                facts.put("weekdayDays", weekdayDays);
                facts.put("weekdayDailyAverage", weekdaySpend.divide(BigDecimal.valueOf(weekdayDays), 2,
                        RoundingMode.HALF_UP));
                facts.put("threshold", WEEKEND_FACTOR);
                result.add(new SpendingPattern(WEEKEND_SPENDING, facts));
            }
        }
        return result;
    }

    static int countDays(LocalDate from, LocalDate to) {
        return (int) (to.toEpochDay() - from.toEpochDay()) + 1;
    }

    static int countWeekendDays(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (MetricsService.isWeekend(day)) {
                count++;
            }
        }
        return count;
    }
}
