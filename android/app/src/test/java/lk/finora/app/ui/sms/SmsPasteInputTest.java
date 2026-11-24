package lk.finora.app.ui.sms;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsParseResult.Reason;
import lk.finora.app.sms.SmsTemplateLoader;

public class SmsPasteInputTest {
    @Test public void parsesOnlyTheFieldsFromTheUniqueFictionalFormat() throws IOException {
        try (var in = getClass().getResourceAsStream("/sms/fictional_formats.json")) {
            var parser = SmsTemplateLoader.load(in);
            var result = (Parsed) SmsPasteInput.parse(parser, message(), () -> false);
            assertEquals(new BigDecimal("9999999999999.09"), result.amount());
            assertEquals("Parsed", result.toString());
            assertEquals(new ManualReview(Reason.CANCELLED), SmsPasteInput.parse(parser, message(), () -> true));
        }
    }

    @Test public void doesNotInventReceiptDateForPastedText() throws IOException {
        String source;
        try (var in = getClass().getResourceAsStream("/sms/fictional_formats.json")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String configured = source.replace("\"MESSAGE\"", "\"RECEIPT\"")
                .replace("\"datePattern\": \"ISO\"", "\"receiptZone\": \"Asia/Colombo\"")
                .replace("{\"name\": \"DATE\", \"delimiter\": \" | \"},", "");
        var parser = SmsTemplateLoader.load(new ByteArrayInputStream(configured.getBytes(StandardCharsets.UTF_8)));
        assertEquals(new ManualReview(Reason.INVALID_FIELDS),
                SmsPasteInput.parse(parser, message().replace("2024-02-29 | ", ""), () -> false));
    }

    private String message() throws IOException {
        try (var in = getClass().getResourceAsStream("/sms/fictional_messages.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")[0];
        }
    }
}
