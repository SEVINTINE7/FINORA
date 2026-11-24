package lk.finora.app.sms;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.Telephony;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Reads only for an explicit scan and returns parsed fields, never message bodies. */
public final class SmsInboxReader {
    public static final int MAX_ROWS = 200;
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    public enum Status { COMPLETE, NO_FORMATS, PERMISSION_REQUIRED, INVALID_RANGE, CANCELLED, UNAVAILABLE }
    public record Scan(Status status, List<SmsParseResult> results) {}

    interface Source {
        Rows query(String selection, String[] arguments, int limit, CancellationSignal cancellation);
    }

    interface Rows extends AutoCloseable {
        boolean next();
        String sender();
        long receivedMillis();
        String body();
        @Override void close();
    }

    private final SmsParser parser;
    private final Source source;
    private final BooleanSupplier permitted;

    public SmsInboxReader(SmsParser parser, ContentResolver resolver, BooleanSupplier permitted) {
        this(parser, (selection, arguments, limit, signal) -> {
            Bundle options = new Bundle();
            options.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
            options.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments);
            options.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, Telephony.Sms.DATE + " DESC");
            options.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
            Cursor cursor = resolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY}, options, signal);
            if (cursor == null) return null;
            return new Rows() {
                @Override public boolean next() { return cursor.moveToNext(); }
                @Override public String sender() { return cursor.getString(0); }
                @Override public long receivedMillis() { return cursor.getLong(1); }
                @Override public String body() { return cursor.getString(2); }
                @Override public void close() { cursor.close(); }
            };
        }, permitted);
    }

    SmsInboxReader(SmsParser parser, Source source, BooleanSupplier permitted) {
        this.parser = parser;
        this.source = source;
        this.permitted = permitted;
    }

    public Scan read(Instant from, Instant to, int maxRows, CancellationSignal signal, BooleanSupplier cancelled) {
        var senders = parser.approvedSenderIds();
        if (senders.isEmpty()) return empty(Status.NO_FORMATS);
        if (from == null || to == null || !from.isBefore(to) || maxRows < 1 || maxRows > MAX_ROWS
                || Duration.between(from, to).compareTo(MAX_RANGE) > 0) return empty(Status.INVALID_RANGE);
        if (cancelled.getAsBoolean()) return empty(Status.CANCELLED);
        if (!permitted.getAsBoolean()) return empty(Status.PERMISSION_REQUIRED);
        try {
            long start = from.toEpochMilli();
            long end = to.toEpochMilli();
            if (start >= end) return empty(Status.INVALID_RANGE);
            var arguments = new ArrayList<>(senders);
            String selection = "address IN (" + String.join(",", Collections.nCopies(senders.size(), "?"))
                    + ") AND date >= ? AND date < ?";
            arguments.add(Long.toString(start));
            arguments.add(Long.toString(end));
            List<SmsParseResult> results = new ArrayList<>();
            try (Rows rows = source.query(selection, arguments.toArray(new String[0]), maxRows, signal)) {
                if (rows == null) return empty(Status.UNAVAILABLE);
                for (int visited = 0; visited < maxRows; visited++) {
                    if (cancelled.getAsBoolean()) return empty(Status.CANCELLED);
                    if (!permitted.getAsBoolean()) return empty(Status.PERMISSION_REQUIRED);
                    if (!rows.next()) break;
                    String sender = rows.sender();
                    long date = rows.receivedMillis();
                    if (!senders.contains(sender) || date < start || date >= end) continue;
                    var parsed = parser.parse(sender, Instant.ofEpochMilli(date), rows.body(), cancelled);
                    if (parsed instanceof SmsParseResult.ManualReview review
                            && review.reason() == SmsParseResult.Reason.CANCELLED) {
                        return empty(Status.CANCELLED);
                    }
                    results.add(parsed);
                }
            }
            if (cancelled.getAsBoolean()) return empty(Status.CANCELLED);
            if (!permitted.getAsBoolean()) return empty(Status.PERMISSION_REQUIRED);
            return new Scan(Status.COMPLETE, Collections.unmodifiableList(results));
        } catch (OperationCanceledException stop) {
            return empty(Status.CANCELLED);
        } catch (SecurityException denied) {
            return empty(Status.PERMISSION_REQUIRED);
        } catch (ArithmeticException invalidRange) {
            return empty(Status.INVALID_RANGE);
        } catch (RuntimeException providerFailure) {
            return empty(Status.UNAVAILABLE);
        }
    }

    private static Scan empty(Status status) { return new Scan(status, Collections.emptyList()); }
}
