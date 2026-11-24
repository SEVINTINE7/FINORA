package lk.finora.app.ui.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.Test;

import lk.finora.app.sms.SmsParseResult.Direction;
import lk.finora.app.sms.SmsParseResult.Parsed;

public class SmsPreviewDraftTest {
    @Test public void retainsExactEditedFieldsAndSelectionWithoutChangingOriginalDirection() {
        var initial = new SmsPreviewDraft(new Parsed(new BigDecimal("1250.75"),
                LocalDate.of(2026, 11, 21), Direction.DEBIT, "Old"));
        var edited = initial.edit(SmsPreviewDraft.amount("9999999999999.09"),
                LocalDate.of(2026, 11, 22), "New", 2L, 3L, false);
        assertTrue(initial.selected());
        assertFalse(edited.selected());
        assertEquals(new BigDecimal("9999999999999.09"), edited.fields().amount());
        assertEquals(LocalDate.of(2026, 11, 22), edited.fields().date());
        assertEquals("New", edited.fields().merchant());
        assertEquals(Long.valueOf(2), edited.accountId());
        assertEquals(Long.valueOf(3), edited.categoryId());
        assertEquals("EXPENSE", edited.categoryKind());
        assertEquals(initial.dedupHash(), edited.dedupHash());
    }

    @Test public void canonicalFingerprintUsesOriginalFieldsAndNormalizesMerchant() {
        var original = new SmsPreviewDraft(new Parsed(new BigDecimal("1250.75"),
                LocalDate.of(2026, 11, 21), Direction.DEBIT, " Cafe\u0301 "));
        var pasted = new SmsPreviewDraft(new Parsed(new BigDecimal("1250.75"),
                LocalDate.of(2026, 11, 21), Direction.DEBIT, "Caf\u00e9"));
        assertEquals("7c8fef0ee5c70b6250c63f417d06d59e0b9ce697a80de8ed7f64dfa4761ef93e", original.dedupHash());
        assertEquals(original.dedupHash(), pasted.dedupHash());
    }

    @Test public void rejectsInvalidOrImpreciseAmounts() {
        for (String invalid : new String[]{"0", "-1", "1e3", "1.009", "2,500", "10000000000000"}) {
            assertNull(SmsPreviewDraft.amount(invalid));
        }
        assertEquals(new BigDecimal("42.10"), SmsPreviewDraft.amount("42.1"));
    }
}
