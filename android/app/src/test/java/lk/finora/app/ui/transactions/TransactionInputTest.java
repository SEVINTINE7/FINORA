package lk.finora.app.ui.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;

import org.junit.Test;

public class TransactionInputTest {
    @Test public void preservesExactLkrAmountAndDate() {
        var edit = TransactionInput.edit(1, null, "EXPENSE", "9999999999999.09",
                "2024-02-29", null, null);
        assertEquals(new BigDecimal("9999999999999.09"), edit.amount());
        assertEquals("2024-02-29", edit.txnDate());
    }

    @Test public void rejectsInvalidAmountsWithoutRounding() {
        for (String amount : new String[]{"0.00", "-1", "0.009", "10000000000000", "1e2"}) {
            assertThrows(IllegalArgumentException.class, () -> TransactionInput.edit(
                    1, null, "INCOME", amount, "2026-11-22", null, null));
        }
    }

    @Test public void rejectsNonCalendarDates() {
        assertThrows(DateTimeParseException.class, () -> TransactionInput.date("2026-02-29"));
        assertThrows(DateTimeParseException.class, () -> TransactionInput.date("2026-2-01"));
    }
}
