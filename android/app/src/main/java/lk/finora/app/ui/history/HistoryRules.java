package lk.finora.app.ui.history;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lk.finora.app.data.repo.MonthlyRepository.Month;

/** T66.3/D70: pure History rules, kept out of the activity for unit tests. */
public final class HistoryRules {
    private HistoryRules() {}

    /** The API returns months oldest first; the cards show newest first. */
    public static List<Month> newestFirst(Month[] rows) {
        List<Month> out = new ArrayList<>(List.of(rows));
        Collections.reverse(out);
        return out;
    }

    /** First and last scored months (oldest → newest) for the summary line, or null when no month has a score. */
    public static Month[] ends(Month[] rows) {
        Month first = null;
        Month last = null;
        for (Month row : rows) {
            if (row.refreshPending() || row.score() == null) continue;
            if (first == null) first = row;
            last = row;
        }
        return first == null ? null : new Month[]{first, last};
    }

    /** Months in the range with a saved snapshot but no score (rebuilding months are not counted). */
    public static int gaps(Month[] rows) {
        int gaps = 0;
        for (Month row : rows) if (!row.refreshPending() && row.score() == null) gaps++;
        return gaps;
    }

    /** Chart x position: whole months after the start of the range. */
    public static long position(YearMonth start, Month row) {
        return java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.parse(row.month()));
    }
}
