package lk.finora.app.ui.transactions;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class TransactionInput {
    private TransactionInput() {}

    static String date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw new DateTimeParseException("Expected an ISO calendar date", value, 0);
        }
        return LocalDate.parse(value).toString();
    }

}
