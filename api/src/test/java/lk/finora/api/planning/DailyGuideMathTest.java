package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyGuideMathTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static BigDecimal m(String amount) { return new BigDecimal(amount); }
    private DailyGuideMath.Result run(String available, String bills, String savings, LocalDate next) {
        return DailyGuideMath.calculate(TODAY, next, m(available), m(bills), m(savings));
    }
    @Test void ordinary() {
        var r = run("15000", "3500", "2000", TODAY.plusDays(10));
        assertThat(r.dailyAllowance()).isEqualByComparingTo("950.00");
        assertThat(r.spendingDays()).isEqualTo(10L);
    }
    @Test void floorsCents() {
        var r = run("100", "0", "0", TODAY.plusDays(3));
        assertThat(r.dailyAllowance()).isEqualByComparingTo("33.33");
        assertThat(r.dailyAllowance().multiply(m("3"))).isLessThanOrEqualTo(r.spendableFunds());
    }
    @Test void shortfall() {
        var r = run("-200", "100", "50", TODAY.plusDays(1));
        assertThat(r.status()).isEqualTo(DailyGuideMath.Status.SHORTFALL);
        assertThat(r.dailyAllowance()).isEqualByComparingTo("0.00");
        assertThat(r.shortfall()).isEqualByComparingTo("350.00");
        assertThat(r.warning()).isTrue();
    }
    @Test void zero() {
        var r = run("100", "50", "50", TODAY.plusDays(1));
        assertThat(r.status()).isEqualTo(DailyGuideMath.Status.NO_SPENDABLE_FUNDS);
        assertThat(r.dailyAllowance()).isEqualByComparingTo("0.00");
    }
    @Test void incomeToday() {
        var r = run("100", "0", "0", TODAY);
        assertThat(r.status()).isEqualTo(DailyGuideMath.Status.INCOME_TODAY);
        assertThat(r.dailyAllowance()).isNull();
    }
    @Test void past() {
        assertThat(run("100", "0", "0", TODAY.minusDays(1)).status()).isEqualTo(DailyGuideMath.Status.INCOME_DATE_PASSED);
    }
    @Test void noIncome() {
        assertThat(run("100", "0", "0", null).status()).isEqualTo(DailyGuideMath.Status.MISSING_NEXT_INCOME_DATE);
    }
    @Test void missingBalance() {
        assertThat(DailyGuideMath.calculate(TODAY, TODAY.plusDays(1), null, m("0"), m("0")).dailyAllowance()).isNull();
    }
    @Test void missingSavings() {
        assertThat(DailyGuideMath.calculate(TODAY, TODAY.plusDays(1), m("100"), m("0"), null).dailyAllowance()).isNull();
    }
    @Test void lastDayOfMonth() {
        var r = DailyGuideMath.calculate(LocalDate.of(2026,11,30), LocalDate.of(2026,12,1), m("100"), m("0"), m("0"));
        assertThat(r.spendingDays()).isEqualTo(1L);
        assertThat(r.dailyAllowance()).isEqualByComparingTo("100.00");
    }
    @Test void leapYear() {
        var r = DailyGuideMath.calculate(LocalDate.of(2028,2,28), LocalDate.of(2028,3,1), m("100"), m("0"), m("0"));
        assertThat(r.spendingDays()).isEqualTo(2L);
    }
    @Test void lessThanOneCentPerDay() {
        var r = run("0.01", "0", "0", TODAY.plusDays(2));
        assertThat(r.dailyAllowance()).isEqualByComparingTo("0.00");
        assertThat(r.warning()).isTrue();
    }
    @Test void aggregateBillsMayExceedSingleInputLimit() {
        var r = run("100", "20000000000000.00", "0", TODAY.plusDays(1));
        assertThat(r.shortfall()).isEqualByComparingTo("19999999999900.00");
    }
}
