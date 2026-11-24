package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.repo.CsvImportRepository.Row;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;

public class CsvImportRepositoryTest {
    @Test public void previewSendsOriginalCsvInFilePart() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            byte[] csv = "CSV sample".getBytes(StandardCharsets.UTF_8);
            var request = CsvImportRepository.previewRequest(server.url("/api/v1/imports/csv"), csv);
            Buffer body = new Buffer();
            request.body().writeTo(body);
            String multipart = body.readUtf8();
            assertEquals("/api/v1/imports/csv/preview", request.url().encodedPath());
            assertTrue(multipart.contains("name=\"file\""));
            assertTrue(multipart.contains("CSV sample"));
        }
    }

    @Test public void confirmPreservesExactAmountAndOccurrenceAndDoesNotReplay() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
            server.enqueue(new MockResponse().setResponseCode(200));
            var request = CsvImportRepository.confirmRequest(server.url("/api/v1/imports/csv"),
                    List.of(row(false, List.of())));
            assertTrue(request.body().isOneShot());
            assertThrows(IOException.class, () -> new OkHttpClient().newCall(request).execute().close());
            assertEquals(1, server.getRequestCount());
            var sent = server.takeRequest();
            assertEquals("/api/v1/imports/csv/confirm", sent.getPath());
            String json = sent.getBody().readUtf8();
            assertTrue(json.contains("\"amount\":9999999999999.09"));
            assertTrue(json.contains("\"occurrence\":2"));
            assertFalse(json.contains("accountName"));
        }
    }

    @Test public void invalidAndDuplicatePreviewRowsCannotBeConfirmed() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            var endpoint = server.url("/api/v1/imports/csv");
            assertThrows(IllegalArgumentException.class, () -> CsvImportRepository.confirmRequest(
                    endpoint, List.of(row(true, List.of()))));
            assertThrows(IllegalArgumentException.class, () -> CsvImportRepository.confirmRequest(
                    endpoint, List.of(row(false, List.of("Bad date")))));
        }
    }

    private static Row row(boolean duplicate, List<String> errors) {
        return new Row(4, "2026-11-23", "EXPENSE", new BigDecimal("9999999999999.09"), 2L,
                "Cash", null, null, null, null, 2, duplicate, errors);
    }
}
