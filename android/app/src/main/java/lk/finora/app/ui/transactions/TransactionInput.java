package lk.finora.app.ui.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import lk.finora.app.data.repo.TransactionRepository.Edit;

final class TransactionInput {
    private TransactionInput() {}

    static String date(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw new DateTimeParseException("Expected an ISO calendar date", value, 0);
        }
        return LocalDate.parse(value).toString();
    }

    static Edit edit(long account, Long category, String type, String amount, String date,
            String merchant, String note) {
        if (amount == null || !amount.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")
                || (merchant != null && merchant.length() > 100) || (note != null && note.length() > 255)) {
            throw new IllegalArgumentException("Invalid transaction fields");
        }
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        return new Edit(account, category, type, value, date(date), merchant, note);
    }
}
