package lk.finora.app.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.junit.Before;
import org.junit.Test;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Parsed;

public class SmsAccuracyTest {
    private static final List<Parsed> EXPECTED = List.of(
            new Parsed(new BigDecimal("1250.75"), LocalDate.of(2026, 11, 21), Direction.DEBIT, "TEST SUPERMARKET"),
            new Parsed(new BigDecimal("4500.25"), LocalDate.of(2026, 11, 21), Direction.DEBIT, null),
            new Parsed(new BigDecimal("750.50"), LocalDate.of(2026, 11, 22), Direction.CREDIT, null));
    private SmsParser parser;
    private List<String> fixtures;

    @Before public void loadApprovedSyntheticFixtures() throws IOException {
        try (var in = Files.newInputStream(Path.of("src/main/assets/sms/bank_formats.json"))) {
            parser = SmsTemplateLoader.load(in);
        }
        fixtures = Files.readAllLines(Path.of("src/test/resources/sms/nsb_synthetic_messages.txt"));
    }

    @Test public void measuresFourFieldsPerFixture() {
        assertEquals(EXPECTED.size(), fixtures.size());
        int correct = 0;
        int total = fixtures.size() * 4;
        for (int i = 0; i < fixtures.size(); i++) {
            if (!(parser.parse("NSB", null, fixtures.get(i)) instanceof Parsed actual)) continue;
            var expected = EXPECTED.get(i);
            if (expected.amount().equals(actual.amount())) correct++;
            if (expected.date().equals(actual.date())) correct++;
            if (expected.direction() == actual.direction()) correct++;
            if (Objects.equals(expected.merchant(), actual.merchant())) correct++;
        }
        System.out.printf(java.util.Locale.ROOT,
                "D35 NSB field accuracy: %d/%d (%.1f%%) across %d synthetic positive fixtures%n",
                correct, total, 100.0 * correct / total, fixtures.size());
        assertTrue("Field accuracy below 90%", correct * 100 >= total * 90);
    }

    @Test public void unsupportedAndMalformedInputsRequireReview() {
        String valid = fixtures.get(0);
        var negatives = List.of("Synthetic unsupported banking message",
                valid.substring(0, valid.length() - 1),
                valid.replace("1,250.75", "1,250.75 or 1,200.00"),
                valid.replace("Debited from", "Reversed from"),
                valid.replace("ATM POS Transaction", "ATM Cash Withdrawal"),
                valid.replace("21/11/2026", "31/11/2026"));
        for (String input : negatives) assertTrue(parser.parse("NSB", null, input) instanceof ManualReview);
        System.out.printf("D35 negative cases routed to manual review: %d/%d%n", negatives.size(), negatives.size());
    }
}
