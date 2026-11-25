package lk.finora.api.advice.ai;

/** Whitelisted aggregate facts. Every value is calculated on the server; the unit says how to read it (D58). */
public enum FactKey {
    INCOME, CONSUMPTION, NEEDS, WANTS, WANTS_REFERENCE, SAVINGS_ALLOCATED, SUPPORTED_SAVINGS,
    SAVINGS_TARGET, SAVINGS_SHORTFALL, AVAILABLE_HEADROOM, AFFORDABLE_SAVINGS,
    UNMARKED_LOAN_BILLS, EMERGENCY_RESERVE, EMERGENCY_NEXT_TARGET, EMERGENCY_GAP,
    GOAL_TARGET, GOAL_CREDITED, GOAL_REMAINING, SPENDING_DEFICIT,

    // D58 goal coaching: the user-selected focus goal (its name never leaves the server)
    FOCUS_GOAL_TARGET, FOCUS_GOAL_SAVED, FOCUS_GOAL_REMAINING, FOCUS_GOAL_MONTHS_LEFT("MONTHS"),
    FOCUS_GOAL_MONTHLY_NEEDED, FOCUS_GOAL_MONTHLY_PACE, FOCUS_GOAL_MONTHS_AT_PACE("MONTHS"),
    FOCUS_GOAL_MONTHS_WITH_PLAN("MONTHS"),
    // Largest want categories this month, the prior 3-month average and a suggested cut
    TOP_WANT_1_SPEND, TOP_WANT_1_AVERAGE, TOP_WANT_1_CUT,
    TOP_WANT_2_SPEND, TOP_WANT_2_AVERAGE, TOP_WANT_2_CUT,
    TOP_WANT_3_SPEND, TOP_WANT_3_AVERAGE, TOP_WANT_3_CUT,
    WANTS_CUT_TOTAL, WANTS_SHARE("PERCENT"), NEEDS_SHARE("PERCENT"),
    // Largest need categories (never suggested for cuts)
    TOP_NEED_1_SPEND, TOP_NEED_1_AVERAGE, TOP_NEED_2_SPEND, TOP_NEED_2_AVERAGE,
    // When in the month the money went: days 1-10, 11-20 and 21 to the end
    SPEND_EARLY, SPEND_MIDDLE, SPEND_LATE, EARLY_SPEND_SHARE("PERCENT"),
    // Suggested split of this month's income
    PLAN_NEEDS, PLAN_WANTS_CAP, PLAN_GOAL_TRANSFER, PLAN_WEEKLY_WANTS, PLAN_BUFFER;

    private final String unit;

    FactKey() { this("LKR"); }
    FactKey(String unit) { this.unit = unit; }

    /** LKR, PERCENT (whole number) or MONTHS (whole number). */
    public String unit() { return unit; }
}
