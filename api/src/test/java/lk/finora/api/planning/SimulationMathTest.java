package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SimulationMathTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 11, 22);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void calculatesCompleteHypotheticalScenario() {
        var result = SimulationMath.calculate(
                money("10000.00"),
                money("1500.00"),
                money("500.00"),
                money("500.00"),
                TODAY);

        assertThat(result.monthlyAmount())
                .isEqualByComparingTo("2500.00");
        assertThat(result.monthsRequired()).isEqualTo(4);
        assertThat(result.finishDate())
                .isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(result.status()).isEqualTo("POSSIBLE");
    }

    @Test
    void roundsPartialMonthsUp() {
        var result = SimulationMath.calculate(
                money("100.00"), money("30.00"),
                money("0.00"), money("0.00"), TODAY);

        assertThat(result.monthsRequired()).isEqualTo(4);
    }

    @Test
    void handlesNoSavingsCapacity() {
        var result = SimulationMath.calculate(
                money("1000.00"), money("0.00"),
                money("0.00"), money("0.00"), TODAY);

        assertThat(result.status())
                .isEqualTo("NO_MONTHLY_CAPACITY");
        assertThat(result.finishDate()).isNull();
    }

    @Test
    void handlesNegativeMonthlyChange() {
        var result = SimulationMath.calculate(
                money("1000.00"), money("100.00"),
                money("-150.00"), money("0.00"), TODAY);

        assertThat(result.monthlyAmount())
                .isEqualByComparingTo("0.00");
        assertThat(result.finishDate()).isNull();
    }

    @Test
    void handlesCompletedGoal() {
        var result = SimulationMath.calculate(
                money("0.00"), money("0.00"),
                money("0.00"), money("0.00"), TODAY);

        assertThat(result.status()).isEqualTo("ALREADY_COMPLETE");
        assertThat(result.monthsRequired()).isZero();
        assertThat(result.finishDate()).isEqualTo(TODAY);
    }

    @Test
    void avoidsMisleadingVeryLongDates() {
        var result = SimulationMath.calculate(
                money("1000000.00"), money("1.00"),
                money("0.00"), money("0.00"), TODAY);

        assertThat(result.status()).isEqualTo("BEYOND_HORIZON");
        assertThat(result.finishDate()).isNull();
    }

    @Test
    void usesCalendarMonthEnd() {
        var result = SimulationMath.calculate(
                money("100.00"), money("100.00"),
                money("0.00"), money("0.00"),
                LocalDate.of(2026, 10, 31));

        assertThat(result.finishDate())
                .isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void rejectsInvalidNegativeBaseline() {
        assertThatThrownBy(() -> SimulationMath.calculate(
                money("100.00"), money("-1.00"),
                money("0.00"), money("0.00"), TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}