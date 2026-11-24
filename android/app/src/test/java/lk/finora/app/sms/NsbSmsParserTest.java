package lk.finora.app.sms;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsParseResult.Reason;

public class NsbSmsParserTest {
    private SmsParser parser;
    private List<String> samples;

    @Before public void loadSyntheticFixturesAndBundledTemplates() throws IOException {
        try (var input = Files.newInputStream(Path.of("src/main/assets/sms/bank_formats.json"))) {
            parser = SmsTemplateLoader.load(input);
        }
        samples = Files.readAllLines(Path.of("src/test/resources/sms/nsb_synthetic_messages.txt"));
    }

    @Test public void extractsOnlyTransactionFieldsForInboxAndPaste() {
        assertEquals(Set.of("NSB"), parser.approvedSenderIds());
        assertBoth(0, "1250.75", 21, Direction.DEBIT, "TEST SUPERMARKET");
        assertBoth(1, "4500.25", 21, Direction.DEBIT, null);
        assertBoth(2, "750.50", 22, Direction.CREDIT, null);
    }

    @Test public void checksExactSenderAndKnownFormat() {
        assertReview(Reason.UNKNOWN_FORMAT, "OTHER", samples.get(0));
        assertReview(Reason.UNKNOWN_FORMAT, "nsb", samples.get(0));
        assertReview(Reason.UNKNOWN_FORMAT, "NSB", "Unknown synthetic format");
        assertReview(Reason.INVALID_FIELDS, "NSB", samples.get(0) + "extra text");
        assertReview(Reason.INVALID_FIELDS, "NSB", samples.get(0).replace("ATM POS Transaction", "Reversal"));
        assertReview(Reason.INVALID_FIELDS, "NSB", samples.get(0).replace("XXXXXXXX0000", "0000"));
    }

    @Test public void rejectsConflictingOrUnsupportedDirectionAndCurrency() {
        String debit = samples.get(0);
        assertReview(Reason.CONFLICTING_INPUT, "NSB", debit.replace("Debited from", "Credited to"));
        assertReview(Reason.CONFLICTING_INPUT, "NSB", debit.replace("Debited from", "Debited from/Credited to"));
        assertReview(Reason.INVALID_FIELDS, "NSB", debit.replace("Debited from", "Reversed from"));
        assertReview(Reason.UNSUPPORTED_CURRENCY, "NSB", debit.replace(",LKR", ",USD"));
    }

    @Test public void rejectsMissingAmbiguousAndMalformedTransactionData() {
        String debit = samples.get(0);
        for (String amount : List.of("", "1,250.75 and 10.50", "12,50.75")) {
            assertReview(Reason.INVALID_FIELDS, "NSB", debit.replace("1,250.75", amount));
        }
        assertReview(Reason.INVALID_FIELDS, "NSB", debit.replace("21/11/2026", "31/11/2026"));
        assertReview(Reason.INVALID_FIELDS, "NSB", debit.replace("14:27", "24:27"));
    }

    private void assertBoth(int index, String amount, int day, Direction direction, String merchant) {
        var expected = new Parsed(new BigDecimal(amount), LocalDate.of(2026, 11, day), direction, merchant);
        assertEquals(expected, parser.parse("NSB", null, samples.get(index)));
        assertEquals(expected, parser.parse(null, null, samples.get(index)));
    }

    private void assertReview(Reason reason, String sender, String body) {
        assertEquals(new ManualReview(reason), parser.parse(sender, null, body));
    }
}
