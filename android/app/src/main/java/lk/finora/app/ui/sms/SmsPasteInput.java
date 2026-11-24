package lk.finora.app.ui.sms;

import java.util.function.BooleanSupplier;

import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsParser;

final class SmsPasteInput {
    private SmsPasteInput() {}

    static SmsParseResult parse(SmsParser parser, CharSequence message, BooleanSupplier cancelled) {
        // Clipboard text carries neither a trusted sender nor a receipt timestamp.
        return parser.parse(null, null, message, cancelled);
    }
}
