package lk.finora.api.transaction;

/** How the record was entered. SMS/PASTE records are only the fields the user confirmed (rule 1). */
public enum TransactionSource {
    MANUAL, SMS, PASTE, CSV
}
