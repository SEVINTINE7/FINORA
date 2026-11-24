package lk.finora.app.ui.sms;

import java.math.BigDecimal;
import java.time.LocalDate;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.Parsed;

record SmsPreviewDraft(Parsed fields, Long accountId, Long categoryId, boolean selected) {
    SmsPreviewDraft(Parsed fields) {
        this(fields, null, null, true);
    }

    static BigDecimal amount(String text) {
        if (!text.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) return null;
        var value = new BigDecimal(text);
        return value.signum() > 0 ? value.setScale(2) : null;
    }

    SmsPreviewDraft edit(BigDecimal amount, LocalDate date, String merchant, Long account,
                         Long category, boolean selected) {
        return new SmsPreviewDraft(new Parsed(amount, date, fields.direction(),
                merchant.isEmpty() ? null : merchant), account, category, selected);
    }

    String categoryKind() {
        return fields.direction() == Direction.DEBIT ? "EXPENSE" : "INCOME";
    }
}
