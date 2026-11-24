package lk.finora.app.ui.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import lk.finora.app.data.repo.TransactionRepository.Page;

public class TransactionPaginationTest {
    @Test public void reconciliationRequiresSuccessfulCurrentPageAfterFilterOrRetry() {
        var pagination = new TransactionViewModel.Pagination();
        assertFalse(pagination.hasCurrentResult());
        pagination.loaded(new Page(Collections.emptyList(), 0, 2));
        assertTrue(pagination.hasCurrentResult());
        pagination.requested(1);
        assertFalse(pagination.hasCurrentResult());
        pagination.loaded(new Page(Collections.emptyList(), 1, 2));
        assertTrue(pagination.hasCurrentResult());
        pagination.reset();
        assertFalse(pagination.hasCurrentResult());
        pagination.requested(0);
        assertFalse(pagination.hasCurrentResult());
        pagination.loaded(new Page(Collections.emptyList(), 0, 0));
        assertTrue(pagination.hasCurrentResult());
    }

    @Test public void failedPageRequestRetriesTargetWhilePreviousPageRemainsVisible() {
        var pagination = new TransactionViewModel.Pagination();
        pagination.loaded(new Page(Collections.emptyList(), 0, 2));
        pagination.requested(1);
        // A failed request leaves the last successfully loaded page on screen.
        assertEquals(0, pagination.page().page());
        assertEquals(1, pagination.retryIndex());

        pagination.loaded(new Page(Collections.emptyList(), 1, 2));
        pagination.requested(0);
        assertEquals(0, pagination.retryIndex());
        pagination.reset();
        assertEquals(0, pagination.page().page());
        assertEquals(0, pagination.retryIndex());
    }
}
