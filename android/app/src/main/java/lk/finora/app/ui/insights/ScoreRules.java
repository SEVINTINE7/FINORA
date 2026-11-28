package lk.finora.app.ui.insights;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;

import lk.finora.app.data.repo.InsightsRepository.Declaration;

/** T66.2/D70: score details rules, kept pure for unit tests. */
public final class ScoreRules {
    private ScoreRules() {}

    /** Tile order on the Score details page. */
    public static final int NOT_SURE = 0;
    public static final int YES = 1;
    public static final int NO = 2;

    /**
     * D70: the page asks the positive question ("Do you have any debt to pay?"), the API stores the negative
     * ({@code noApplicableDebt}). Yes → false, No → true, Not sure → null.
     */
    public static Boolean noneFromAnswer(int answer) {
        return answer == YES ? Boolean.FALSE : answer == NO ? Boolean.TRUE : null;
    }

    public static int answerFromNone(Boolean none) {
        return none == null ? NOT_SURE : none ? NO : YES;
    }

    /** The calendar opens on the chosen date, otherwise the earlier of today and the month's last day. */
    public static LocalDate pickerStart(String chosen, YearMonth month, LocalDate today) {
        if (chosen != null && !chosen.isEmpty()) {
            try {
                return LocalDate.parse(chosen);
            } catch (DateTimeException ignored) {
                // Fall through to the default below.
            }
        }
        LocalDate end = month.atEndOfMonth();
        return end.isAfter(today) ? today : end;
    }

    /** Both blank clears the reserve; otherwise an amount with at most two decimals and a date in the month, not after today. */
    public static Declaration parse(String amount, String date, YearMonth month, LocalDate today,
                                    Boolean noDebt, Boolean noGoals) {
        amount = amount.trim();
        date = date.trim();
        if (amount.isEmpty() && date.isEmpty()) return new Declaration(null, null, noDebt, noGoals);
        if (!amount.matches("[0-9]{1,13}([.,][0-9]{1,2})?") || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException();
        }
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (!YearMonth.from(parsed).equals(month) || parsed.isAfter(today)) throw new IllegalArgumentException();
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException(invalid);
        }
        return new Declaration(new BigDecimal(amount.replace(',', '.')), date, noDebt, noGoals);
    }
}
