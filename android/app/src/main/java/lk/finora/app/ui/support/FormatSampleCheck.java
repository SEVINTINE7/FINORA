package lk.finora.app.ui.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;

/** Local authoring and sample validation. It cannot publish a candidate. */
final class FormatSampleCheck {
    private FormatSampleCheck() {}

    static Result check(SmsParser approved, String definition, String syntheticSample) throws IOException {
        var candidate = SmsTemplateLoader.load(new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8)));
        return new Result(candidate.hasSameTemplates(approved), candidate.parse("NSB", null, syntheticSample));
    }

    record Result(boolean approvedDefinition, SmsParseResult outcome) {}
}
