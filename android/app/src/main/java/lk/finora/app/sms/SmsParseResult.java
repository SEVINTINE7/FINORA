package lk.finora.app.sms;

import java.math.BigDecimal;
import java.time.LocalDate;

public sealed interface SmsParseResult permits SmsParseResult.Parsed, SmsParseResult.ManualReview {
    enum Direction { DEBIT, CREDIT }
    enum Reason { UNKNOWN_FORMAT, AMBIGUOUS_FORMAT, INVALID_FIELDS, CONFLICTING_INPUT,
        UNSUPPORTED_CURRENCY, INPUT_TOO_LARGE, LIMIT_REACHED, CANCELLED }

    record Parsed(BigDecimal amount, LocalDate date, Direction direction,
                  String merchant) implements SmsParseResult {
        // Avoid including extracted content if a caller prints a result.
        @Override public String toString() { return "Parsed"; }
    }

    record ManualReview(Reason reason) implements SmsParseResult {}
}
