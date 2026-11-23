package lk.finora.app.sms;

import java.time.ZoneId;
import java.util.List;

record SmsTemplate(String id, int version, List<String> senderIds, NumberStyle numberStyle,
                   DatePattern datePattern, DateSource dateSource, ZoneId receiptZone,
                   Markers markers, List<Field> fields) {
    enum NumberStyle {
        DOT_DECIMAL('.', null), COMMA_GROUPED('.', ','),
        COMMA_DECIMAL(',', null), DOT_GROUPED(',', '.');

        final char decimal;
        final Character grouping;

        NumberStyle(char decimal, Character grouping) {
            this.decimal = decimal;
            this.grouping = grouping;
        }
    }

    enum DatePattern { ISO, DAY_MONTH_YEAR }
    enum DateSource { MESSAGE, RECEIPT }
    enum Name { AMOUNT, DATE, DIRECTION, MERCHANT, CURRENCY }
    record Markers(String prefix, String debit, String credit) {}
    record Field(Name name, String delimiter) {}
}
