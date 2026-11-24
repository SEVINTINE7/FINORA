package lk.finora.app.sms;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

import lk.finora.app.sms.SmsTemplate.DatePattern;
import lk.finora.app.sms.SmsTemplate.DateSource;
import lk.finora.app.sms.SmsTemplate.NumberStyle;

final class SmsFieldParser {
    private static final DateTimeFormatter DAY_FIRST =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter DAY_FIRST_WITH_TIME = DateTimeFormatter
            .ofPattern("dd/MM/uuuu 'at' HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private SmsFieldParser() {}

    static BigDecimal amount(String token, NumberStyle style) {
        int decimal = token.indexOf(style.decimal);
        String whole = decimal < 0 ? token : token.substring(0, decimal);
        String fraction = decimal < 0 ? "" : token.substring(decimal + 1);
        if (decimal >= 0 && (fraction.isEmpty() || fraction.length() > 2 || !digits(fraction))) return null;
        Character grouping = style.grouping;
        if (grouping != null && whole.indexOf(grouping) >= 0) {
            StringBuilder ungrouped = new StringBuilder();
            int start = 0;
            for (int end = 0; end <= whole.length(); end++) {
                if (end != whole.length() && whole.charAt(end) != grouping) continue;
                int width = end - start;
                if (start == 0 ? width < 1 || width > 3 : width != 3) return null;
                ungrouped.append(whole, start, end);
                start = end + 1;
            }
            whole = ungrouped.toString();
        }
        if (whole.isEmpty() || whole.length() > 13 || !digits(whole)) return null;
        BigDecimal value = new BigDecimal(whole + "." + (fraction.isEmpty() ? "0" : fraction));
        return value.signum() > 0 ? value.setScale(2) : null;
    }

    private static boolean digits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            if (digit < '0' || digit > '9') return false;
        }
        return true;
    }

    static LocalDate date(String token, SmsTemplate template, Instant receivedAt) {
        try {
            LocalDate date;
            if (template.dateSource() == DateSource.RECEIPT) {
                if (receivedAt == null) return null;
                date = receivedAt.atZone(template.receiptZone()).toLocalDate();
            } else if (template.datePattern() == DatePattern.ISO && token.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                date = LocalDate.parse(token, DateTimeFormatter.ISO_LOCAL_DATE);
            } else if (template.datePattern() == DatePattern.DAY_MONTH_YEAR && token.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) {
                date = LocalDate.parse(token, DAY_FIRST);
            } else if (template.datePattern() == DatePattern.DAY_MONTH_YEAR_WITH_TIME
                    && token.matches("[0-9]{2}/[0-9]{2}/[0-9]{4} at [0-9]{2}:[0-9]{2}")) {
                date = LocalDateTime.parse(token, DAY_FIRST_WITH_TIME).toLocalDate();
            } else {
                return null;
            }
            return date.getYear() >= 1 && date.getYear() <= 9999 ? date : null;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    static boolean validMerchant(String merchant) {
        if (merchant.length() > 100) return false;
        for (int i = 0; i < merchant.length(); i++) {
            if (Character.isISOControl(merchant.charAt(i))) return false;
        }
        return true;
    }
}
