package lk.finora.app.ui.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.repo.MonthlyRepository.Fact;
import lk.finora.app.data.repo.MonthlyRepository.Month;
import lk.finora.app.ui.advice.AdviceRules;

public final class HistoryRulesTest {
    private static Month month(String month, Integer score, boolean pending) {
        BigDecimal value = pending ? null : new BigDecimal("1.00");
        return new Month(month, value, pending ? null : BigDecimal.ZERO, value, score, List.of(), pending, null);
    }

    @Test public void cardsAreNewestFirstAndTheSummaryUsesScoredMonths() {
        Month[] rows = {month("2026-05", 61, false), month("2026-07", null, false), month("2026-09", 78, false),
                month("2026-10", null, true)};
        var newest = HistoryRules.newestFirst(rows);
        assertEquals("2026-10", newest.get(0).month());
        assertEquals("2026-05", newest.get(3).month());
        var ends = HistoryRules.ends(rows);
        assertEquals("2026-05", ends[0].month());
        assertEquals("2026-09", ends[1].month());
        assertEquals(1, HistoryRules.gaps(rows));
        assertEquals(4, HistoryRules.position(YearMonth.of(2026, 5), rows[2]));
        assertNull(HistoryRules.ends(new Month[]{month("2026-07", null, false)}));
    }

    @Test public void adviceFiguresAppearOnceAndSectionsKeepTheirHeadings() {
        var figures = AdviceRules.distinct(List.of(
                new Fact("A", new BigDecimal("180000.00"), "LKR", "2026-10"),
                new Fact("B", new BigDecimal("8.00"), "MONTHS", "2026-10"),
                new Fact("C", new BigDecimal("180000"), "LKR", "2026-10"),
                new Fact("D", new BigDecimal("8"), "PERCENT", "2026-10")));
        assertEquals(3, figures.size());
        assertEquals("8", AdviceRules.plain(new BigDecimal("8.00")));
        assertEquals(R.string.advice_section_goal, AdviceRules.section("GOAL_PLAN"));
        assertEquals(0, AdviceRules.section("REVIEW_HIGH_WANTS"));
        assertEquals(R.drawable.finora_ic_info, AdviceRules.icon("UNKNOWN_ACTION"));
    }
}
