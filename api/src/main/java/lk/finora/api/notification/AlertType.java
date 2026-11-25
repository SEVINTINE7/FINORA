package lk.finora.api.notification;

/** T34.2 alert events. The push carries only this name; the app shows a fixed message with no amounts. */
public enum AlertType {
    BUDGET_NEAR,
    BUDGET_OVER,
    BILL_DUE,
    GOAL_DATE_NEAR
}
