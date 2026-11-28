package lk.finora.app.ui.goals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.GoalDto;

public class GoalListViewModelTest {
    private static GoalDto goal(long id, String status, String date, String target, String saved) {
        BigDecimal t = new BigDecimal(target);
        BigDecimal s = new BigDecimal(saved);
        BigDecimal left = t.subtract(s).max(BigDecimal.ZERO);
        int percent = Math.min(100, s.multiply(BigDecimal.valueOf(100)).divide(t, 0, java.math.RoundingMode.DOWN)
                .intValue());
        return new GoalDto(id, "Goal " + id, t, date, status, s, left, percent);
    }

    @Test public void focusGoalFirstThenInProgressReachedAndCancelled() {
        var cancelled = goal(1, "CANCELLED", null, "85000.00", "12000.00");
        var emergency = goal(2, "ACTIVE", null, "150000.00", "34500.00");
        var reached = goal(3, "DONE", null, "40000.00", "40000.00");
        var laptop = goal(4, "ACTIVE", "2027-03-31", "120000.00", "64000.00");
        var ordered = GoalListViewModel.ordered(List.of(cancelled, emergency, reached, laptop));
        assertEquals(List.of(laptop, emergency, reached, cancelled), ordered);
        assertEquals(laptop, GoalListViewModel.focus(ordered));
    }

    @Test public void totalsCountOnlyGoalsInProgress() {
        var totals = GoalListViewModel.totals(List.of(
                goal(1, "ACTIVE", "2027-03-31", "120000.00", "64000.00"),
                goal(2, "ACTIVE", null, "150000.00", "34500.00"),
                goal(3, "DONE", null, "40000.00", "40000.00"),
                goal(4, "CANCELLED", null, "85000.00", "12000.00")));
        assertEquals(new BigDecimal("98500.00"), totals.saved());
        assertEquals(new BigDecimal("270000.00"), totals.target());
        assertEquals(new BigDecimal("171500.00"), totals.toGo());
        assertEquals(2, totals.count());
        assertEquals(36, totals.percent());
    }

    @Test public void noGoalsInProgressMeansNoFocusAndZeroTotals() {
        var rows = List.of(goal(1, "DONE", null, "100.00", "100.00"));
        assertNull(GoalListViewModel.focus(rows));
        var totals = GoalListViewModel.totals(rows);
        assertEquals(0, totals.count());
        assertEquals(0, totals.percent());
    }
}
