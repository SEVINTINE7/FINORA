package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class DashboardRepositoryTest {
    @Test public void keepsExactNegativeNetAndUncategorisedExpense() throws Exception {
        try (var response = response(snapshot("2026-01", "9999999999999.99"))) {
            var result = DashboardRepository.read(response, YearMonth.of(2026, 1));
            assertEquals(new BigDecimal("-9999999999999.99"), result.net());
            assertEquals(null, result.byCategory().get(0).categoryId());
            assertEquals("2025-08", result.trend().get(0).month());
        }
    }

    @Test public void rejectsStaleMonthAndMismatchedCategoryTotal() {
        try (var response = response(snapshot("2026-02", "9999999999999.99"))) {
            assertThrows(IOException.class, () -> DashboardRepository.read(response, YearMonth.of(2026, 1)));
        }
        try (var response = response(snapshot("2026-01", "1.00"))) {
            assertThrows(IOException.class, () -> DashboardRepository.read(response, YearMonth.of(2026, 1)));
        }
    }

    private static String snapshot(String month, String categoryTotal) {
        return """
                {"month":"%s","income":0.00,"expense":9999999999999.99,"net":-9999999999999.99,
                "byCategory":[{"categoryId":null,"total":%s}],"trend":[
                {"month":"2025-08","income":0,"expense":0,"net":0},
                {"month":"2025-09","income":0,"expense":0,"net":0},
                {"month":"2025-10","income":0,"expense":0,"net":0},
                {"month":"2025-11","income":0,"expense":0,"net":0},
                {"month":"2025-12","income":0,"expense":0,"net":0},
                {"month":"2026-01","income":0,"expense":9999999999999.99,"net":-9999999999999.99}]}
                """.formatted(month, categoryTotal);
    }

    private static Response response(String body) {
        return new Response.Builder().request(new Request.Builder().url("https://example.test").build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(body, null)).build();
    }
}
