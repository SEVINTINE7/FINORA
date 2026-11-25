package lk.finora.api.smsformat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import lk.finora.api.common.error.ApiException;
import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;

final class ApprovedSmsFixtures {
    private static final List<Parsed> EXPECTED = List.of(
            new Parsed(new BigDecimal("1250.75"), LocalDate.of(2026, 11, 21), Direction.DEBIT, "TEST SUPERMARKET"),
            new Parsed(new BigDecimal("4500.25"), LocalDate.of(2026, 11, 21), Direction.DEBIT, null),
            new Parsed(new BigDecimal("750.50"), LocalDate.of(2026, 11, 22), Direction.CREDIT, null));
    private static final List<String> MESSAGES = load();
    static final String REVISION = SmsFormatService.sha256(String.join("\n", MESSAGES) + "\n" + EXPECTED.stream()
            .map(row -> row.amount() + "|" + row.date() + "|" + row.direction() + "|" + row.merchant())
            .reduce("", (left, right) -> left + right + "\n"));

    private ApprovedSmsFixtures() {}

    static SmsParser parser(String definition) {
        if (definition.length() > 65_536) throw ApiException.badRequest("SMS definition exceeds size limit");
        try {
            return SmsTemplateLoader.load(new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException invalid) {
            throw ApiException.badRequest("Invalid SMS definition");
        }
    }

    static List<Boolean> validate(String definition) {
        SmsParser parser = parser(definition);
        return java.util.stream.IntStream.range(0, MESSAGES.size())
                .mapToObj(index -> EXPECTED.get(index).equals(parser.parse("NSB", null, MESSAGES.get(index))))
                .toList();
    }

    private static List<String> load() {
        try (var input = ApprovedSmsFixtures.class.getResourceAsStream("/sms/nsb_synthetic_messages.txt")) {
            if (input == null) throw new IllegalStateException("Missing approved NSB fixture resource");
            var lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            if (lines.size() != EXPECTED.size()) throw new IllegalStateException("Invalid approved NSB fixture count");
            return lines;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load approved NSB fixtures", failure);
        }
    }
}
