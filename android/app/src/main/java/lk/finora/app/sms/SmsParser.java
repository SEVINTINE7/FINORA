package lk.finora.app.sms;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsParseResult.Reason;
import lk.finora.app.sms.SmsTemplate.Name;

/** Local parsing only. No message, sender or template match is retained after a call. */
public final class SmsParser {
    private static final int MAX_MESSAGE_BYTES = 2 * 1024;
    private static final int MAX_OPERATIONS = 250_000;
    private final List<SmsTemplate> templates;

    SmsParser(List<SmsTemplate> templates) { this.templates = templates; }

    public java.util.Set<String> approvedSenderIds() {
        var senders = new java.util.LinkedHashSet<String>();
        for (SmsTemplate template : templates) senders.addAll(template.senderIds());
        return java.util.Collections.unmodifiableSet(senders);
    }

    public SmsParseResult parse(String sender, Instant receivedAt, CharSequence body) {
        return parse(sender, receivedAt, body, () -> false);
    }

    /** A caller can supply a cancellation or deadline predicate. Null sender means sender unavailable. */
    public SmsParseResult parse(String sender, Instant receivedAt, CharSequence body, BooleanSupplier cancelled) {
        Budget budget = new Budget(cancelled);
        try {
            budget.step();
            if (body == null || body.length() == 0) return new ManualReview(Reason.INVALID_FIELDS);
            if (body.length() > MAX_MESSAGE_BYTES) return new ManualReview(Reason.INPUT_TOO_LARGE);
            if (sender != null && sender.length() > SmsTemplateLoader.MAX_LITERAL) {
                return new ManualReview(Reason.UNKNOWN_FORMAT);
            }
            String message = boundedMessage(body, budget);
            SmsTemplate match = null;
            EnumMap<Name, String> fields = null;
            boolean recognizedPrefix = false;
            for (SmsTemplate template : templates) {
                budget.step();
                if (sender != null && !template.senderIds().contains(sender)) continue;
                String prefix = template.markers().prefix();
                if (!matches(message, prefix, 0, budget)) continue;
                recognizedPrefix = true;
                EnumMap<Name, String> candidate = extract(template, message, prefix.length(), budget);
                if (candidate == null) continue;
                if (match != null) return new ManualReview(Reason.AMBIGUOUS_FORMAT);
                match = template;
                fields = candidate;
            }
            if (match == null) return new ManualReview(recognizedPrefix ? Reason.INVALID_FIELDS : Reason.UNKNOWN_FORMAT);
            budget.step();
            return parseFields(fields, match, receivedAt, budget);
        } catch (Stopped stop) {
            return new ManualReview(stop.reason);
        }
    }

    private static String boundedMessage(CharSequence body, Budget budget) throws Stopped {
        StringBuilder message = new StringBuilder(body.length());
        int bytes = 0;
        for (int i = 0; i < body.length(); i++) {
            budget.step();
            char c = body.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= body.length() || !Character.isLowSurrogate(body.charAt(i + 1))) {
                    throw new Stopped(Reason.INVALID_FIELDS);
                }
                message.append(c).append(body.charAt(++i));
                bytes += 4;
            } else {
                if (Character.isLowSurrogate(c) || c == '\0') throw new Stopped(Reason.INVALID_FIELDS);
                message.append(c);
                bytes += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
            }
            if (bytes > MAX_MESSAGE_BYTES) throw new Stopped(Reason.INPUT_TOO_LARGE);
        }
        return message.toString();
    }

    private static EnumMap<Name, String> extract(SmsTemplate template, String message, int start, Budget budget)
            throws Stopped {
        var values = new EnumMap<Name, String>(Name.class);
        String suffix = template.markers().suffix();
        int boundary = message.length() - suffix.length();
        if (boundary < start || !matches(message, suffix, boundary, budget)) return null;
        for (SmsTemplate.Field field : template.fields()) {
            String delimiter = field.delimiter();
            int end = start;
            int lastStart = boundary - delimiter.length();
            while (end <= lastStart && !matches(message, delimiter, end, budget)) end++;
            if (end > lastStart) return null;
            values.put(field.name(), message.substring(start, end).trim());
            start = end + delimiter.length();
        }
        return start == boundary ? values : null;
    }

    private static boolean matches(String message, String literal, int start, Budget budget) throws Stopped {
        if (literal.length() > message.length() - start) return false;
        for (int i = 0; i < literal.length(); i++) {
            budget.step();
            if (message.charAt(start + i) != literal.charAt(i)) return false;
        }
        return true;
    }

    private static SmsParseResult parseFields(EnumMap<Name, String> values,
            SmsTemplate template, Instant receivedAt, Budget budget) throws Stopped {
        String directionToken = values.get(Name.DIRECTION);
        Direction direction;
        if (directionToken.equals(template.markers().debit())) direction = Direction.DEBIT;
        else if (directionToken.equals(template.markers().credit())) direction = Direction.CREDIT;
        else {
            boolean conflict = directionToken.contains(template.markers().debit())
                    && directionToken.contains(template.markers().credit());
            return new ManualReview(conflict ? Reason.CONFLICTING_INPUT : Reason.INVALID_FIELDS);
        }
        Direction requiredDirection = template.markers().requiredDirection();
        if (requiredDirection != null && direction != requiredDirection) {
            return new ManualReview(Reason.CONFLICTING_INPUT);
        }
        if (!validContext(values, template)) return new ManualReview(Reason.INVALID_FIELDS);
        if (!"LKR".equals(values.get(Name.CURRENCY))) return new ManualReview(Reason.UNSUPPORTED_CURRENCY);
        var amount = SmsFieldParser.amount(values.get(Name.AMOUNT), template.numberStyle());
        var date = SmsFieldParser.date(values.get(Name.DATE), template, receivedAt);
        String merchant = values.get(Name.MERCHANT);
        if (amount == null || date == null || (merchant != null && (!SmsFieldParser.validMerchant(merchant)
                || (requiredDirection != null && merchant.isEmpty())))) {
            return new ManualReview(Reason.INVALID_FIELDS);
        }
        budget.step();
        return new Parsed(amount, date, direction, merchant == null || merchant.isEmpty() ? null : merchant);
    }

    private static boolean validContext(EnumMap<Name, String> values, SmsTemplate template) {
        String recipient = values.get(Name.RECIPIENT);
        String account = values.get(Name.ACCOUNT);
        String balance = values.get(Name.BALANCE);
        return (recipient == null || (!recipient.isEmpty() && SmsFieldParser.validMerchant(recipient)))
                && (account == null || account.matches("X{8}[0-9]{4}"))
                && (balance == null || SmsFieldParser.amount(balance, template.numberStyle()) != null);
    }

    private static final class Budget {
        private int remaining = MAX_OPERATIONS;
        private final BooleanSupplier cancelled;
        Budget(BooleanSupplier cancelled) { this.cancelled = cancelled; }
        void step() throws Stopped {
            if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new Stopped(Reason.CANCELLED);
            if (--remaining < 0) throw new Stopped(Reason.LIMIT_REACHED);
        }
    }

    private static final class Stopped extends Exception {
        final Reason reason;
        Stopped(Reason reason) { this.reason = reason; }
    }
}
