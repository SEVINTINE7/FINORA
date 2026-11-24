package lk.finora.app.ui.goals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.data.api.GoalDto;

public class GoalRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test public void targetDateIsOptionalButMustBeAfterToday() {
        assertTrue(GoalRules.validTargetDate("", TODAY));
        assertTrue(GoalRules.validTargetDate("2026-11-24", TODAY));
        assertFalse(GoalRules.validTargetDate("2026-11-23", TODAY)); // today is not "after today"
        assertFalse(GoalRules.validTargetDate("2026-02-30", TODAY)); // not a real date
        assertFalse(GoalRules.validTargetDate("24/11/2026", TODAY));
    }

    @Test public void contributionDateIsTodayOrEarlier() {
        assertTrue(GoalRules.validContributionDate("2026-11-23", TODAY));
        assertTrue(GoalRules.validContributionDate("2025-01-01", TODAY));
        assertFalse(GoalRules.validContributionDate("2026-11-24", TODAY));
        assertFalse(GoalRules.validContributionDate("", TODAY));
        assertNull(GoalRules.parseDate("2026-13-01"));
    }

    @Test public void editIsBlockedOnlyWhileTheStoredDateHasPassed() {
        assertTrue(GoalRules.canEditAsIs(null, TODAY));
        assertTrue(GoalRules.canEditAsIs("2026-12-01", TODAY));
        assertFalse(GoalRules.canEditAsIs("2026-11-23", TODAY));
        assertFalse(GoalRules.canEditAsIs("2026-01-01", TODAY));
    }

    @Test public void amountsAndLabels() {
        assertEquals(new BigDecimal("250000.00"), GoalRules.parseAmount("250000"));
        assertThrows(IllegalArgumentException.class, () -> GoalRules.parseAmount("0.00"));
        assertThrows(IllegalArgumentException.class, () -> GoalRules.parseAmount("1.234"));
        assertEquals(R.string.goal_done, GoalRules.statusLabel("DONE"));
        assertEquals(R.string.goal_cancelled, GoalRules.statusLabel("CANCELLED"));
        assertEquals(R.string.goal_active, GoalRules.statusLabel("ACTIVE"));
    }

    @Test public void editKeepsACancelledGoalCancelledAndEmptyDateMeansNone() {
        GoalDto.Write keep = GoalFormViewModel.body(" Laptop ", new BigDecimal("1.00"), " ", true);
        assertEquals("Laptop", keep.name());
        assertNull(keep.targetDate());
        assertEquals(Boolean.TRUE, keep.cancelled());
        assertNull(GoalFormViewModel.body("Laptop", new BigDecimal("1.00"), "2027-01-01", false).cancelled());
    }
}
