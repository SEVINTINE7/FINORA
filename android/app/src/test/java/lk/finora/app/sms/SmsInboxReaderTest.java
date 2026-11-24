package lk.finora.app.sms;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import lk.finora.app.sms.SmsInboxReader.Status;

public class SmsInboxReaderTest {
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = START.plus(31, ChronoUnit.DAYS);

    @Test public void emptyBundleNeverQueriesEvenWhenPermitted() throws IOException {
        var empty = SmsTemplateLoader.load(new ByteArrayInputStream(
                "{\"schemaVersion\":1,\"templates\":[]}".getBytes(StandardCharsets.UTF_8)));
        var reader = new SmsInboxReader(empty, (s, args, limit, cancel) -> { fail("Queried inbox"); return null; },
                () -> true);
        assertEquals(Status.NO_FORMATS, reader.read(START, END, 200, null, () -> false).status());
    }

    @Test public void rejectsPermissionRangeRowCapAndCancellationBeforeQuery() throws IOException {
        var reader = new SmsInboxReader(parser(), (s, args, limit, cancel) -> { fail("Queried inbox"); return null; },
                () -> false);
        assertEquals(Status.PERMISSION_REQUIRED, reader.read(START, END, 200, null, () -> false).status());
        assertEquals(Status.INVALID_RANGE, reader.read(START, END.plusNanos(1), 200, null, () -> false).status());
        assertEquals(Status.INVALID_RANGE, reader.read(START, END, 201, null, () -> false).status());
        assertEquals(Status.CANCELLED, reader.read(START, END, 200, null, () -> true).status());
    }

    @Test public void parameterizesFiltersRechecksProviderRowsAndClosesAtCap() throws IOException {
        var rows = new SyntheticRows();
        var reader = new SmsInboxReader(parser(), (s, args, limit, cancel) -> {
            assertEquals("address IN (?) AND date >= ? AND date < ?", s);
            assertArrayEquals(new String[]{"FINORA-FICTION-ONLY", Long.toString(START.toEpochMilli()),
                    Long.toString(END.toEpochMilli())}, args);
            assertEquals(200, limit);
            return rows;
        }, () -> true);
        var scan = reader.read(START, END, 200, null, () -> false);
        assertEquals(Status.COMPLETE, scan.status());
        assertEquals(198, scan.results().size());
        assertEquals(198, rows.bodyReads);
        assertEquals(200, rows.visited);
        assertTrue(rows.closed);
        assertTrue(scan.results().get(0) instanceof SmsParseResult.Parsed);
    }

    @Test public void discardsPartialResultsAfterPermissionRevocation() throws IOException {
        var permitted = new AtomicBoolean(true);
        var rows = new SyntheticRows() {
            @Override public String body() {
                permitted.set(false);
                return super.body();
            }
        };
        var reader = new SmsInboxReader(parser(), (s, args, limit, cancel) -> rows, permitted::get);
        var scan = reader.read(START, END, 200, null, () -> false);
        assertEquals(Status.PERMISSION_REQUIRED, scan.status());
        assertTrue(scan.results().isEmpty());
        assertEquals(1, rows.bodyReads);
        assertTrue(rows.closed);
    }

    private static SmsParser parser() throws IOException {
        try (var in = SmsInboxReaderTest.class.getResourceAsStream("/sms/fictional_formats.json")) {
            return SmsTemplateLoader.load(in);
        }
    }

    private static class SyntheticRows implements SmsInboxReader.Rows {
        int visited;
        int bodyReads;
        boolean closed;
        @Override public boolean next() { visited++; return true; }
        @Override public String sender() { return visited == 1 ? "UNAPPROVED" : "FINORA-FICTION-ONLY"; }
        @Override public long receivedMillis() { return (visited == 2 ? END : START).toEpochMilli(); }
        @Override public String body() {
            bodyReads++;
            return "FICTION QA | D | LKR | 27.09 | 2026-11-23 | Fiction Shop | END";
        }
        @Override public void close() { closed = true; }
    }
}
