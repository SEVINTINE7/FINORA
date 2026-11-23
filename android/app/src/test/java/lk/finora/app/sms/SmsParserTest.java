package lk.finora.app.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsParseResult.Reason;

public class SmsParserTest {
    private static final String SENDER = "FINORA-FICTION-ONLY";

    @Test public void extractsExactFictionalDebitAndCredit() throws IOException {
        var parser = load(formats());
        var samples = messages();
        var debit = (Parsed) parser.parse(SENDER, null, samples[0]);
        assertEquals(new BigDecimal("9999999999999.09"), debit.amount());
        assertEquals(LocalDate.of(2024, 2, 29), debit.date());
        assertEquals(Direction.DEBIT, debit.direction());
        assertEquals("Make-Believe Shop", debit.merchant());
        assertFalse(debit.toString().contains("Make-Believe Shop"));
        var credit = (Parsed) parser.parse(null, null, samples[1]);
        assertEquals(new BigDecimal("42.10"), credit.amount());
        assertEquals(Direction.CREDIT, credit.direction());
        assertNull(credit.merchant());
    }

    @Test public void unknownSenderUnknownFormatAndUnconsumedTextRequireReview() throws IOException {
        var parser = load(formats());
        assertReview(Reason.UNKNOWN_FORMAT, parser.parse("UNKNOWN", null, messages()[0]));
        assertReview(Reason.UNKNOWN_FORMAT, parser.parse(null, null, "Unrecognized synthetic input"));
        assertReview(Reason.INVALID_FIELDS, parser.parse(SENDER, null, messages()[0] + " unexplained"));
    }

    @Test public void rejectsConflictingDirectionNonLkrAndMalformedDatesOrAmounts() throws IOException {
        var parser = load(formats());
        String sample = messages()[0];
        assertReview(Reason.CONFLICTING_INPUT, parser.parse(SENDER, null, sample.replace("| D |", "| D/C |")));
        assertReview(Reason.UNSUPPORTED_CURRENCY, parser.parse(SENDER, null, sample.replace("LKR", "USD")));
        assertReview(Reason.INVALID_FIELDS, parser.parse(SENDER, Instant.EPOCH, sample.replace("2024-02-29", "2025-02-29")));
        for (String invalid : new String[]{"0", "-1", "1e2", "1.001", "01,2.50", "10000000000000.00",
                "1.00 and 2.00"}) {
            assertReview(Reason.INVALID_FIELDS, parser.parse(SENDER, null, sample.replace("9,999,999,999,999.09", invalid)));
        }
    }

    @Test public void usesOnlyExplicitLocaleAndReceiptDateRules() throws IOException {
        String alternate = formats().replace("COMMA_GROUPED", "DOT_GROUPED").replace("\"MESSAGE\"", "\"RECEIPT\"")
                .replace("\"datePattern\": \"ISO\"", "\"receiptZone\": \"Asia/Colombo\"")
                .replace("{\"name\": \"DATE\", \"delimiter\": \" | \"},", "");
        var parser = load(alternate);
        String sample = messages()[0].replace("9,999,999,999,999.09", "1.234,50").replace("2024-02-29 | ", "");
        assertReview(Reason.INVALID_FIELDS, parser.parse(SENDER, null, sample));
        var parsed = (Parsed) parser.parse(SENDER, Instant.parse("2026-11-23T20:00:00Z"), sample);
        assertEquals(new BigDecimal("1234.50"), parsed.amount());
        assertEquals(LocalDate.of(2026, 11, 24), parsed.date());
    }

    @Test public void rejectsAmbiguousSenderlessMatchAndDuplicateMappings() throws IOException {
        String template = entry();
        String alternate = template.replace("fictional-lab", "fictional-alt")
                .replace("FINORA-FICTION-ONLY", "FINORA-FICTION-OTHER");
        var parser = load(bundle(template + "," + alternate));
        assertReview(Reason.AMBIGUOUS_FORMAT, parser.parse(null, null, messages()[0]));
        assertEquals(Direction.DEBIT, ((Parsed) parser.parse(SENDER, null, messages()[0])).direction());
        assertThrows(IOException.class, () -> load(bundle(template + "," + template.replace("fictional-lab", "same-mapping"))));
    }

    @Test public void enforcesUtf8SizeOperationAndCancellationBounds() throws IOException {
        var parser = load(formats());
        assertReview(Reason.INPUT_TOO_LARGE, parser.parse(SENDER, null, "අ".repeat(700)));
        assertReview(Reason.INVALID_FIELDS, parser.parse(SENDER, null, "\uD800"));
        var count = new AtomicInteger();
        assertReview(Reason.CANCELLED, parser.parse(SENDER, null, messages()[0], () -> count.incrementAndGet() > 20));

        String longDelimiter = entry().replace("\"delimiter\": \" | \"", "\"delimiter\": \"" + "a".repeat(63) + "b\"");
        var slow = load(bundle(longDelimiter + ","
                + longDelimiter.replace("fictional-lab", "fictional-two").replace(SENDER, "FICTION-2") + ","
                + longDelimiter.replace("fictional-lab", "fictional-three").replace(SENDER, "FICTION-3")));
        assertReview(Reason.LIMIT_REACHED, slow.parse(null, null, "FICTION QA | " + "a".repeat(2000)));
    }

    @Test public void rejectsInvalidVersionFieldsBundleAndTemplateLimits() throws IOException {
        String source = formats();
        assertThrows(IOException.class, () -> load(source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertThrows(IOException.class, () -> load(source.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1,\"schemaVersion\": 1")));
        assertThrows(IOException.class, () -> load(source.replace("\"DIRECTION\"", "\"AMOUNT\"")));
        assertThrows(IOException.class, () -> load(source.replace("\"prefix\"", "\"regex\"")));
        assertThrows(IOException.class, () -> load(source.replace("FICTION QA | ", "x".repeat(65))));
        assertThrows(IOException.class, () -> load(source + " ".repeat(64 * 1024)));
        assertThrows(IOException.class, () -> load(bundle((entry() + ",").repeat(32) + entry())));
        String senders = "\"fiction-1\"";
        for (int i = 2; i <= 17; i++) senders += ",\"fiction-" + i + "\"";
        String tooManySenders = source.replace("\"" + SENDER + "\"", senders);
        assertThrows(IOException.class, () -> load(tooManySenders));
        assertThrows(IOException.class, () -> SmsTemplateLoader.load(new ByteArrayInputStream(new byte[]{(byte) 0xFF})));
    }

    @Test public void productionBundleRecognizesNoBankFormats() throws IOException {
        try (var input = Files.newInputStream(Path.of("src/main/assets/sms/bank_formats.json"))) {
            assertReview(Reason.UNKNOWN_FORMAT, SmsTemplateLoader.load(input).parse(SENDER, null, messages()[0]));
        }
    }

    private static SmsParser load(String json) throws IOException {
        return SmsTemplateLoader.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String formats() throws IOException { return resource("fictional_formats.json"); }
    private static String[] messages() throws IOException { return resource("fictional_messages.txt").split("\\R"); }

    private static String entry() throws IOException {
        String source = formats();
        return source.substring(source.indexOf('{', source.indexOf('[')), source.lastIndexOf(']')).trim();
    }

    private static String bundle(String entries) { return "{\"schemaVersion\":1,\"templates\":[" + entries + "]}"; }

    private static String resource(String name) throws IOException {
        try (var input = SmsParserTest.class.getResourceAsStream("/sms/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertReview(Reason reason, SmsParseResult result) {
        assertEquals(new ManualReview(reason), result);
    }
}
