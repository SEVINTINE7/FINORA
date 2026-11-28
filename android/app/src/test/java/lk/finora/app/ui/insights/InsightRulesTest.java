package lk.finora.app.ui.insights;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;

import lk.finora.app.R;

public final class InsightRulesTest {
    @Test public void patternsKeepTheirTitlesAndUnknownTypesAreSkipped() {
        assertEquals(R.string.insight_wants, InsightRules.title("HIGH_WANTS"));
        assertEquals(R.string.insight_payday, InsightRules.title("PAYDAY_SPENDING"));
        assertEquals(0, InsightRules.title("SOMETHING_NEW"));
        assertEquals(R.string.insight_expense_share, InsightRules.shareText("PAYDAY_SPENDING"));
        assertEquals(R.string.insight_income_share, InsightRules.shareText("HIGH_WANTS"));
        assertNull(InsightRules.shareKeys("WEEKEND_SPENDING"));
    }

    @Test public void sharesAndComparisonsMatchTheApiFacts() {
        assertEquals("33.91%", InsightRules.percent(new BigDecimal("0.3391"), Locale.ENGLISH));
        assertEquals("40%", InsightRules.percent(new BigDecimal("0.40"), Locale.ENGLISH));
        assertEquals("2.7", InsightRules.times(Map.of("weekendDailyAverage", new BigDecimal("3672.22"),
                "weekdayDailyAverage", new BigDecimal("1379.55"))));
        assertNull(InsightRules.times(Map.of("weekendDailyAverage", BigDecimal.ONE)));
    }

    @Test public void scorePartsShowPointsOutOfTheirWeight() {
        assertEquals(1000, InsightRules.partProgress(new BigDecimal("45.00"), 45));
        assertEquals(765, InsightRules.partProgress(new BigDecimal("7.65"), 10));
        assertEquals(0, InsightRules.partProgress(null, 20));
        assertEquals("6.30", InsightRules.points(new BigDecimal("6.3")));
        assertEquals(R.string.score_reserve_basis, InsightRules.reason("EMERGENCY_USER_DECLARED"));
    }

    @Test public void scoreDetailsAskThePositiveQuestionButStoreTheSameValue() {
        assertEquals(Boolean.FALSE, ScoreRules.noneFromAnswer(ScoreRules.YES));
        assertEquals(Boolean.TRUE, ScoreRules.noneFromAnswer(ScoreRules.NO));
        assertNull(ScoreRules.noneFromAnswer(ScoreRules.NOT_SURE));
        assertEquals(ScoreRules.NO, ScoreRules.answerFromNone(true));
        assertEquals(ScoreRules.YES, ScoreRules.answerFromNone(false));
        assertEquals(ScoreRules.NOT_SURE, ScoreRules.answerFromNone(null));
    }

    @Test public void theCalendarOpensInsideTheMonthAndNotAfterToday() {
        var today = LocalDate.of(2026, 11, 28);
        assertEquals(LocalDate.of(2026, 10, 31), ScoreRules.pickerStart("", YearMonth.of(2026, 10), today));
        assertEquals(today, ScoreRules.pickerStart(null, YearMonth.of(2026, 11), today));
        assertEquals(LocalDate.of(2026, 10, 5), ScoreRules.pickerStart("2026-10-05", YearMonth.of(2026, 10), today));
    }
}
