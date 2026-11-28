package lk.finora.app.ui.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.Test;

public class ScoreDeclarationTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 11);
    private static final LocalDate TODAY = MONTH.atDay(24);

    @Test public void preservesZeroAndNullableDeclarations() {
        var blank = ScoreRules.parse("", "", MONTH, TODAY, null, false);
        assertNull(blank.emergencyReserve());
        assertNull(blank.noApplicableDebt());
        assertEquals(Boolean.FALSE, blank.noApplicableGoals());
        var zero = ScoreRules.parse("0,00", "2026-11-24", MONTH, TODAY, true, null);
        assertEquals(new BigDecimal("0.00"), zero.emergencyReserve());
        assertEquals("2026-11-24", zero.reserveAsOf());
        assertEquals(Boolean.TRUE, zero.noApplicableDebt());
    }

    @Test public void rejectsOutOfPeriodFutureAndInexactInputs() {
        assertThrows(IllegalArgumentException.class, () -> parse("1.001", "2026-11-24"));
        assertThrows(IllegalArgumentException.class, () -> parse("1.00", "2026-11-25"));
        assertThrows(IllegalArgumentException.class, () -> parse("1.00", "2026-10-20"));
        assertThrows(IllegalArgumentException.class, () -> parse("1.00", ""));
    }

    private static void parse(String amount, String date) {
        ScoreRules.parse(amount, date, MONTH, TODAY, null, null);
    }
}
