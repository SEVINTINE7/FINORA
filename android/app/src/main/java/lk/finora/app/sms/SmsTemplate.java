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

    enum DatePattern { ISO, DAY_MONTH_YEAR, DAY_MONTH_YEAR_WITH_TIME }
    enum DateSource { MESSAGE, RECEIPT }
    enum Name { AMOUNT, DATE, DIRECTION, MERCHANT, CURRENCY, RECIPIENT, ACCOUNT, BALANCE }
    record Markers(String prefix, String debit, String credit, String suffix, SmsParseResult.Direction requiredDirection) {}
    record Field(Name name, String delimiter) {}
}
