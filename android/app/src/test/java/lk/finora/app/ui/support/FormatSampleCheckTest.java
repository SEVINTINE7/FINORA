package lk.finora.app.ui.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsTemplateLoader;
import org.junit.Test;
import static org.junit.Assert.*;

public class FormatSampleCheckTest {
    @Test public void validatesSyntheticSamplesAndSeparatesPreviewFromOfficialApproval() throws Exception {
        var definitionPath = Path.of("src/main/assets/sms/bank_formats.json");
        var definition = Files.readString(definitionPath);
        try (var input = Files.newInputStream(definitionPath)) {
            var approved = SmsTemplateLoader.load(input);
            for (String sample : Files.readAllLines(Path.of("src/test/resources/sms/nsb_synthetic_messages.txt"))) {
                var result = FormatSampleCheck.check(approved, definition, sample);
                assertTrue(result.approvedDefinition());
                assertTrue(result.outcome() instanceof SmsParseResult.Parsed parsed
                        && parsed.equals(approved.parse("NSB", null, sample)));
            }
            assertTrue(FormatSampleCheck.check(approved, definition, "Synthetic unmatched text")
                    .outcome() instanceof SmsParseResult.ManualReview);
            assertFalse(FormatSampleCheck.check(approved, definition.replace("nsb-pos-debit", "synthetic-candidate"),
                    "Synthetic unmatched text").approvedDefinition());
            assertThrows(IOException.class, () -> FormatSampleCheck.check(approved, "{}", "Synthetic unmatched text"));
        }
    }
}
