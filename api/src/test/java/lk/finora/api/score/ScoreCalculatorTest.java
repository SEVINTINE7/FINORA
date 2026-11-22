package lk.finora.api.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import lk.finora.api.score.dto.ScoreResponse;

class ScoreCalculatorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static BigDecimal d(String s) { return s == null ? null : new BigDecimal(s); }
    private ScoreResponse score(String i, String c, String s, String n, String due, String paid,
            String target, String credit, String reserve) {
        return ScoreCalculator.calculate("2026-11", new ScoreCalculator.Inputs(d(i), d(c).add(d(s)), d(s), d(n),
                d(due), d(paid), d(target), d(credit), d(reserve), reserve == null ? null : DATE));
    }

    @ParameterizedTest
    @CsvSource({
        // Hand-calculated D20 fixtures: I,C,S,N,due,marked,target,credit,reserve,spending,saving,total
        "40000,39600,400,30000,0,0,0,0,0,30.60,2.00,50", // 32.60/65*100
        "20000,19800,200,15000,0,0,10000,2500,0,30.60,2.00,45", // 36.35/80*100
        "40000,40000,0,35000,0,0,0,0,0,27.00,0.00,42",
        "40000,38000,2000,30000,0,0,0,0,15000,45.00,10.00,92",
        "40000,44000,0,36000,0,0,0,0,0,24.55,0.00,38",
        "60000,62000,1000,40000,10000,5000,10000,2500,20000,26.13,0.00,45",
        "100000,90000,5000,60000,15000,15000,100000,50000,30000,45.00,10.00,88",
        "100000,90000,5000,60000,15000,0,100000,50000,30000,45.00,10.00,68",
        "100000,90000,5000,60000,15000,15000,100000,50000,30000,45.00,10.00,88", // net worth not an input
        "100000,90000,5000,60000,15000,15000,100000,100000,180000,45.00,10.00,100",
        "50000,0,2500,10000,0,0,0,0,0,45.00,10.00,85",
        "50000,47500.01,2499.99,10000,0,0,0,0,0,45.00,10.00,85"
    })
    void syntheticMonths(String i, String c, String s, String n, String due, String paid, String target,
            String credit, String reserve, String spending, String saving, int total) {
        ScoreResponse r = score(i, c, s, n, due, paid, target, credit, reserve);
        assertThat(r.total()).isEqualTo(total);
        assertThat(r.parts().get(0).points()).isEqualByComparingTo(spending);
        assertThat(r.parts().get(3).points()).isEqualByComparingTo(saving);
        assertThat(r.parts()).allSatisfy(p -> {
            if (p.points() != null) assertThat(p.points()).isBetween(d("0.00"), BigDecimal.valueOf(p.weight()));
        });
        assertThat(r.scoredWeight() + r.missingWeight() + r.notApplicableWeight()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"0,0.00", "1,0.00", "2500,2.50", "5000,5.00", "7500,6.25",
            "10000,7.50", "20000,8.75", "30000,10.00", "999999,10.00"})
    void emergencyMilestonesAndInterpolation(String reserve, String expected) {
        ScoreResponse r = score("50000", "45000", "2500", "10000", "0", "0", "0", "0", reserve);
        assertThat(r.parts().get(4).points()).isEqualByComparingTo(expected);
        assertThat(r.parts().get(4).reasonKey()).isEqualTo("EMERGENCY_USER_DECLARED");
    }

    @Test
    void zeroIncomeKeepsIndependentPartsAndNullTotal() {
        ScoreResponse r = score("0", "20000", "0", "18000", "5000", "0", "10000", "2500", "0");
        assertThat(r.total()).isNull();
        assertThat(r.missingWeight()).isEqualTo(55);
        assertThat(r.parts().get(0).reasonKey()).isEqualTo("ZERO_INCOME");
        assertThat(r.parts().get(2).points()).isEqualByComparingTo("3.75");
    }

    @Test
    void missingReserveAndZeroNeedsAreNotPerfectOrNotApplicable() {
        ScoreResponse missing = score("40000", "38000", "2000", "30000", "0", "0", "0", "0", null);
        ScoreResponse zero = score("20000", "19000", "500", "0", "0", "0", "0", "0", "5000");
        assertThat(missing.total()).isNull();
        assertThat(missing.parts().get(4).reasonKey()).isEqualTo("EMERGENCY_DECLARATION_MISSING");
        assertThat(zero.total()).isNull();
        assertThat(zero.parts().get(4).reasonKey()).isEqualTo("ESSENTIAL_BASELINE_UNAVAILABLE");
    }

    @Test
    void unknownDebtAndGoalsDoNotNormalizeAwayMissingInformation() {
        ScoreResponse r = score("50000", "45000", "2500", "10000", null, "0", null, "0", "5000");
        assertThat(r.total()).isNull();
        assertThat(r.missingWeight()).isEqualTo(35);
        assertThat(r.notApplicableWeight()).isZero();
    }

    @Test
    void worstRecordedMonthRoundsToZeroWithoutNegativePoints() {
        ScoreResponse r = score("0.01", "9999999999999.99", "0", "10000", "1000", "0", "1000", "0", "0");
        assertThat(r.total()).isZero();
        assertThat(r.parts()).allSatisfy(p -> assertThat(p.points()).isEqualByComparingTo("0.00"));
    }

    @Test
    void repeatedInputIsDeterministicAndNeverCreditsExtraDebtOrGoalAmounts() {
        ScoreResponse a = score("100000", "90000", "5000", "60000", "15000", "20000", "100000", "150000", "180000");
        assertThat(a).isEqualTo(score("100000", "90000", "5000", "60000", "15000", "20000", "100000", "150000", "180000"));
        assertThat(a.total()).isEqualTo(100);
    }
}
