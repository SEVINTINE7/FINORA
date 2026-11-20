package lk.finora.api.goal;

/** ACTIVE and DONE are set by the app from the saved amount; only the user can set CANCELLED. */
public enum GoalStatus {
    ACTIVE,
    DONE,
    CANCELLED
}
