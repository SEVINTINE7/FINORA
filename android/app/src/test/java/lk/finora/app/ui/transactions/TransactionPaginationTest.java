package lk.finora.app.ui.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.repo.TransactionRepository.Page;
import lk.finora.app.data.repo.TransactionRepository.Row;

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

    @Test public void loadMoreAppendsPagesAndSkipsShiftedDuplicates() {
        var pagination = new TransactionViewModel.Pagination();
        pagination.loaded(new Page(List.of(row(1), row(2)), 0, 3));
        assertTrue(pagination.hasMore());
        assertEquals(1, pagination.loadedPages());
        // A new record elsewhere shifted row 2 onto page 1 as well.
        pagination.loaded(new Page(List.of(row(2), row(3)), 1, 3));
        assertEquals(List.of(1L, 2L, 3L), ids(pagination));
        assertTrue(pagination.hasMore());
        pagination.loaded(new Page(List.of(row(4)), 2, 3));
        assertFalse(pagination.hasMore());
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(pagination));
    }

    @Test public void reloadingFirstPageReplacesRows() {
        var pagination = new TransactionViewModel.Pagination();
        pagination.loaded(new Page(List.of(row(1), row(2)), 0, 2));
        pagination.loaded(new Page(List.of(row(3)), 1, 2));
        pagination.loaded(new Page(List.of(row(2)), 0, 1));
        assertEquals(List.of(2L), ids(pagination));
        assertFalse(pagination.hasMore());
        pagination.reset();
        assertTrue(pagination.items().isEmpty());
        assertFalse(pagination.hasMore());
    }

    private static Row row(long id) {
        return new Row(id, 1, null, "EXPENSE", new BigDecimal("10.00"), "2026-11-20", null, null);
    }

    private static List<Long> ids(TransactionViewModel.Pagination pagination) {
        return pagination.items().stream().map(Row::id).toList();
    }
}
