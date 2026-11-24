package lk.finora.app.ui.goals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import lk.finora.app.R;

/** Checks and labels shared by the goal screens (T19.3). Mirrors the API rules in D16. */
final class GoalRules {
    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private GoalRules() {}

    static LocalDate today() {
        return LocalDate.now(COLOMBO);
    }

    /** LKR, more than zero, at most 2 decimals and 13 whole digits. */
    static BigDecimal parseAmount(String value) {
        String text = value.trim();
        if (!text.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Invalid amount");
        BigDecimal result = new BigDecimal(text).setScale(2);
        if (result.signum() <= 0) throw new IllegalArgumentException("Amount must be more than zero");
        return result;
    }

    /** Strict YYYY-MM-DD; null when the text is not a real date. */
    static LocalDate parseDate(String value) {
        String text = value.trim();
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Target date is optional; if given it must be after today (on create and on every edit, D16). */
    static boolean validTargetDate(String value, LocalDate today) {
        if (value.trim().isEmpty()) return true;
        LocalDate date = parseDate(value);
        return date != null && date.isAfter(today);
    }

    /** A contribution has already been saved, so its date is today or earlier. */
    static boolean validContributionDate(String value, LocalDate today) {
        LocalDate date = parseDate(value);
        return date != null && !date.isAfter(today);
    }

    /** Any PUT (also cancel/resume) is refused while a stored target date is today or earlier. */
    static boolean canEditAsIs(String storedTargetDate, LocalDate today) {
        return storedTargetDate == null || LocalDate.parse(storedTargetDate).isAfter(today);
    }

    static int statusLabel(String status) {
        switch (status) {
            case "DONE": return R.string.goal_done;
            case "CANCELLED": return R.string.goal_cancelled;
            default: return R.string.goal_active;
        }
    }

    static int statusColor(String status) {
        switch (status) {
            case "DONE": return R.color.finora_green;
            case "CANCELLED": return R.color.finora_muted;
            default: return R.color.finora_ink;
        }
    }

    static String plain(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
