package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import lk.finora.app.data.repo.TransactionRepository.Filters;
import okhttp3.HttpUrl;

public class TransactionRepositoryTest {
    @Test public void includesAllSelectedFiltersAndZeroBasedPage() {
        HttpUrl endpoint = HttpUrl.get("https://example.test/api/v1/transactions");
        HttpUrl url = TransactionRepository.pageUrl(endpoint,
                new Filters("2026-11-01", "2026-11-30", "EXPENSE", 42L, 77L), 2);
        assertEquals("2026-11-01", url.queryParameter("from"));
        assertEquals("2026-11-30", url.queryParameter("to"));
        assertEquals("EXPENSE", url.queryParameter("type"));
        assertEquals("42", url.queryParameter("categoryId"));
        assertEquals("77", url.queryParameter("accountId"));
        assertEquals("2", url.queryParameter("page"));
    }

    @Test public void omitsUnsetFilters() {
        HttpUrl url = TransactionRepository.pageUrl(HttpUrl.get("https://example.test/api/v1/transactions"),
                new Filters(null, null, null, null, null), 0);
        assertEquals(1, url.querySize());
        assertNull(url.queryParameter("type"));
        assertEquals("0", url.queryParameter("page"));
    }
}
