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
    @Test public void reportContainsOnlyExplicitNullMetadataAndStableErrorCode() throws Exception {
        var request = SmsDiagnosticRepository.reportBody(Code.from(new ManualReview(Reason.AMBIGUOUS_FORMAT)));
        assertTrue(request.isOneShot());
        var bytes = new Buffer();
        request.writeTo(bytes);
        var json = JsonParser.parseString(bytes.readUtf8()).getAsJsonObject();
        assertEquals(Set.of("bankCode", "formatVersion", "errorCode"), json.keySet());
        assertTrue(json.get("bankCode").isJsonNull());
        assertTrue(json.get("formatVersion").isJsonNull());
        assertEquals("FORMAT_AMBIGUOUS", json.get("errorCode").getAsString());
        assertNull(Code.from(new ManualReview(Reason.CANCELLED)));
    }
}
