package lk.finora.app.data.repo;

import com.google.gson.JsonParser;
import java.util.Set;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Code;
import lk.finora.app.sms.SmsParseResult.ManualReview;
import lk.finora.app.sms.SmsParseResult.Reason;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class SmsDiagnosticRepositoryTest {
    @Test public void everyReportUsesOnlyApprovedBankAndUnknownFormatVersion() throws Exception {
        for (Code code : Code.values()) {
            for (var bank : java.util.Arrays.asList(null, lk.finora.app.sms.SmsInboxReader.BankCode.NSB)) {
                var bytes = new Buffer();
                SmsDiagnosticRepository.reportBody(code, bank).writeTo(bytes);
                var payload = JsonParser.parseString(bytes.readUtf8()).getAsJsonObject();
                assertEquals(Set.of("bankCode", "formatVersion", "errorCode"), payload.keySet());
                assertEquals(code.name(), payload.get("errorCode").getAsString());
                assertTrue(payload.get("formatVersion").isJsonNull());
                if (bank == null) assertTrue(payload.get("bankCode").isJsonNull());
                else assertEquals(bank.name(), payload.get("bankCode").getAsString());
            }
        }
    }

    @Test public void reportContainsOnlyExplicitNullMetadataAndStableErrorCode() throws Exception {
        var request = SmsDiagnosticRepository.reportBody(Code.from(new ManualReview(Reason.AMBIGUOUS_FORMAT)), null);
        assertTrue(request.isOneShot());
        var bytes = new Buffer();
        request.writeTo(bytes);
        var json = JsonParser.parseString(bytes.readUtf8()).getAsJsonObject();
        assertEquals(Set.of("bankCode", "formatVersion", "errorCode"), json.keySet());
        assertTrue(json.get("bankCode").isJsonNull());
        assertTrue(json.get("formatVersion").isJsonNull());
        assertEquals("FORMAT_AMBIGUOUS", json.get("errorCode").getAsString());
        assertNull(Code.from(new ManualReview(Reason.CANCELLED)));
        var inbox = new Buffer();
        SmsDiagnosticRepository.reportBody(Code.FORMAT_UNRECOGNIZED,
                lk.finora.app.sms.SmsInboxReader.BankCode.NSB).writeTo(inbox);
        var metadata = JsonParser.parseString(inbox.readUtf8()).getAsJsonObject();
        assertEquals("NSB", metadata.get("bankCode").getAsString());
        assertTrue(metadata.get("formatVersion").isJsonNull());
    }
}
