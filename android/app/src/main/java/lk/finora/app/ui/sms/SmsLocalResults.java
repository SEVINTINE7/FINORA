package lk.finora.app.ui.sms;

import android.content.Context;

import java.io.IOException;

import lk.finora.app.R;
import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;

final class SmsLocalResults {
    private SmsLocalResults() {}

    static SmsParser load(Context context) throws IOException {
        try (var input = context.getAssets().open("sms/bank_formats.json")) {
            return SmsTemplateLoader.load(input);
        }
    }

    static String text(Context context, SmsParseResult result) {
        if (result instanceof SmsParseResult.Parsed parsed) {
            String record = context.getString(R.string.sms_extracted_record,
                    parsed.amount().toPlainString(), parsed.date().toString(),
                    context.getString(parsed.direction() == SmsParseResult.Direction.DEBIT
                            ? R.string.sms_direction_debit : R.string.sms_direction_credit));
            return parsed.merchant() == null ? record
                    : record + "\n" + context.getString(R.string.sms_extracted_merchant, parsed.merchant());
        }
        return context.getString(R.string.sms_needs_review);
    }
}
