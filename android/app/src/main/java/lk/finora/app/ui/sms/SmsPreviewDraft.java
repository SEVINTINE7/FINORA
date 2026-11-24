package lk.finora.app.ui.sms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.Parsed;

record SmsPreviewDraft(Parsed fields, Long accountId, Long categoryId, boolean selected, String dedupHash) {
    SmsPreviewDraft(Parsed fields) {
        this(fields, null, null, true, fingerprint(fields));
    }

    static BigDecimal amount(String text) {
        if (!text.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) return null;
        var value = new BigDecimal(text);
        return value.signum() > 0 ? value.setScale(2) : null;
    }

    SmsPreviewDraft edit(BigDecimal amount, LocalDate date, String merchant, Long account,
                         Long category, boolean selected) {
        return new SmsPreviewDraft(new Parsed(amount, date, fields.direction(),
                merchant.isEmpty() ? null : merchant), account, category, selected, dedupHash);
    }

    private static String fingerprint(Parsed fields) {
        String merchant = fields.merchant() == null ? ""
                : Normalizer.normalize(fields.merchant().trim(), Normalizer.Form.NFC);
        String value = String.join("\n", "FINORA-SMS-v1", fields.date().toString(), fields.direction().name(),
                fields.amount().setScale(2).toPlainString(), merchant);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(digest.length * 2);
            for (byte digit : digest) {
                hex.append(Character.forDigit((digit >>> 4) & 15, 16));
                hex.append(Character.forDigit(digit & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    @Override public String toString() { return "SmsPreviewDraft"; }

    String categoryKind() {
        return fields.direction() == Direction.DEBIT ? "EXPENSE" : "INCOME";
    }
}
