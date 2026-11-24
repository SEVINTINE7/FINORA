package lk.finora.app.ui.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.repo.TransactionRepository.SmsBatchItem;
import lk.finora.app.data.repo.TransactionRepository.SmsTransaction;

public class SmsPendingBatchTest {
    @Test public void retainsFrozenBatchForSameLoginAndClearsAfterReplacement() {
        var pending = new SmsPendingBatch();
        var login = new Object();
        pending.bind(login);
        var item = new SmsBatchItem("a".repeat(64), new SmsTransaction(1, null, "EXPENSE",
                new BigDecimal("1250.75"), "2026-11-21", null, "PASTE"));
        var draft = new ArrayList<>(List.of(item));
        pending.retain(draft);
        draft.clear();
        pending.bind(login);
        assertEquals(List.of(item), pending.items());
        pending.bind(new Object());
        assertTrue(pending.items().isEmpty());
    }

    @Test public void newProcessStateStartsEmptyAndScreenDisposalClears() {
        var pending = new SmsPendingBatch();
        pending.bind(new Object());
        pending.retain(List.of(new SmsBatchItem("a".repeat(64), new SmsTransaction(1, null,
                "INCOME", new BigDecimal("1.25"), "2026-11-22", null, "SMS"))));
        assertTrue(new SmsPendingBatch().items().isEmpty());
        pending.onCleared();
        assertTrue(pending.items().isEmpty());
    }
}
