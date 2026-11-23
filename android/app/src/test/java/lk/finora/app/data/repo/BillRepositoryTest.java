package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.Test;

import lk.finora.app.data.api.BillDto;
import okhttp3.HttpUrl;

public class BillRepositoryTest {
    private static final HttpUrl BILLS = HttpUrl.get("https://example.test/api/v1/bills");

    @Test public void urlsCarryTheMonth() {
        assertEquals("2026-11", BillRepository.monthUrl(BILLS, "2026-11").queryParameter("month"));
        HttpUrl paid = BillRepository.paidUrl(BILLS, 42, "2026-02");
        assertEquals("/api/v1/bills/42/paid", paid.encodedPath());
        assertEquals("2026-02", paid.queryParameter("month"));
    }

    @Test public void acceptsAValidBillAndRejectsBrokenOnes() throws IOException {
        BillRepository.validate(bill(31, "2026-02-28"));
        assertThrows(IOException.class, () -> BillRepository.validate(bill(0, "2026-02-01")));
        assertThrows(IOException.class, () -> BillRepository.validate(bill(32, "2026-02-01")));
        assertThrows(IOException.class, () -> BillRepository.validate(bill(5, null)));
        assertThrows(IOException.class, () -> BillRepository.validate(null));
    }

    private static BillDto bill(int dueDay, String dueDate) {
        return new BillDto(1, "Rent", new BigDecimal("25000.00"), dueDay, null, "2026-02", dueDate, false);
    }
}
