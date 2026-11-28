package lk.finora.app.ui.planning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import lk.finora.app.R;

public final class PlanningRulesTest {
    @Test public void lessMakesTheTypedChangeNegativeAndEmptyMeansNone() {
        assertEquals(new BigDecimal("-5000.00"), PlanningRules.monthlyChange(new BigDecimal("5000.00"), true));
        assertEquals(new BigDecimal("2000.00"), PlanningRules.monthlyChange(new BigDecimal("2000.00"), false));
        assertEquals(new BigDecimal("0.00"), PlanningRules.monthlyChange(null, true));
    }

    @Test public void calendarOpensAtTheChosenDateOrTomorrow() {
        var today = LocalDate.of(2026, 11, 28);
        assertEquals(LocalDate.of(2026, 11, 29), PlanningRules.pickerStart(null, today));
        assertEquals(LocalDate.of(2026, 12, 8), PlanningRules.pickerStart("2026-12-08", today));
        assertEquals(LocalDate.of(2026, 11, 29), PlanningRules.pickerStart("not a date", today));
    }

    @Test public void finishAndReferenceMonthsUseTheFullMonthName() {
        assertEquals("February 2027", PlanningRules.monthYear("2027-02-28", Locale.ENGLISH));
        assertEquals("November 2026", PlanningRules.monthYear("2026-11", Locale.ENGLISH));
        assertEquals("", PlanningRules.monthYear(null, Locale.ENGLISH));
    }

    @Test public void everyGuideStatusHasItsOwnMessage() {
        assertEquals(0, PlanningRules.guideMessage("READY", false));
        assertEquals(R.string.planning_zero, PlanningRules.guideMessage("READY", true));
        assertEquals(R.string.planning_zero, PlanningRules.guideMessage("NO_SPENDABLE_FUNDS", true));
        assertEquals(R.string.planning_overcommitted, PlanningRules.guideMessage("SHORTFALL", false));
        assertEquals(R.string.planning_missing_funds, PlanningRules.guideMessage("MISSING_BALANCE", false));
        assertEquals(R.string.planning_missing_savings, PlanningRules.guideMessage("MISSING_PLANNED_SAVINGS", false));
        assertEquals(R.string.planning_date_missing, PlanningRules.guideMessage("MISSING_NEXT_INCOME_DATE", false));
        assertEquals(R.string.planning_income_today, PlanningRules.guideMessage("INCOME_TODAY", false));
        assertEquals(R.string.planning_income_passed, PlanningRules.guideMessage("INCOME_DATE_PASSED", false));
        assertEquals(R.string.planning_confirm_needed, PlanningRules.guideMessage("BILL_CONFIRMATION_REQUIRED", false));
        assertEquals(R.string.planning_bills_changed, PlanningRules.guideMessage("BILLS_CHANGED", false));
        assertEquals(R.string.planning_missing_arrears, PlanningRules.guideMessage("MISSING_ADDITIONAL_BILLS", false));
        assertEquals(R.string.records_failed, PlanningRules.guideMessage("SOMETHING_NEW", false));
        assertTrue(PlanningRules.needsConfirmation("BILLS_CHANGED"));
        assertFalse(PlanningRules.needsConfirmation("READY"));
    }

    @Test public void everySimulationStatusHasItsOwnMessage() {
        assertEquals(0, PlanningRules.simulationMessage("POSSIBLE"));
        assertEquals(R.string.planning_completed, PlanningRules.simulationMessage("ALREADY_COMPLETE"));
        assertEquals(R.string.planning_no_capacity, PlanningRules.simulationMessage("NO_MONTHLY_CAPACITY"));
        assertEquals(R.string.planning_horizon, PlanningRules.simulationMessage("BEYOND_HORIZON"));
        assertEquals(R.string.records_failed, PlanningRules.simulationMessage(null));
    }
}
