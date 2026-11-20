package lk.finora.api.analytics;

import java.math.BigDecimal;

/**
 * All spending metrics for one user and one month (T21). Money is exact (scale 2).
 *
 * <ul>
 * <li>needs / wants: expenses in NEED or WANT categories; unclassified = no category, or a category
 *     that is neither (e.g. Savings, Other)</li>
 * <li>fixedBills: the user's active monthly bills (planned amounts; count = number of bills)</li>
 * <li>leftOver = income - expense (can be negative); savedToCategory = expenses in the 'Savings' category</li>
 * <li>paydaySpend: expenses on an income day or the 2 days after it (D10); weekend = Sat-Sun</li>
 * <li>Ratios have 4 decimals (0.4123 = 41.23%) and are null when income is 0 (T21.3)</li>
 * </ul>
 */
public record MonthMetrics(
        String month,
        Metric income,
        Metric expense,
        Metric needs,
        Metric wants,
        Metric unclassified,
        Metric fixedBills,
        Metric leftOver,
        Metric savedToCategory,
        Metric paydaySpend,
        Metric weekendSpend,
        Metric weekdaySpend,
        BigDecimal wantsRatio,
        BigDecimal fixedBillsRatio,
        BigDecimal savingsRate) {
}
