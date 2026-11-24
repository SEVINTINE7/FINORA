package lk.finora.app.ui.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;

import org.junit.Test;

public class TransactionInputTest {
    @Test public void preservesExactLkrAmountAndDate() {
        assertEquals(new BigDecimal("9999999999999.09"),
                TransactionFormValidator.parseAmount("9999999999999.09", '.'));
        assertEquals("2024-02-29", TransactionInput.date("2024-02-29"));
    }

    @Test public void rejectsInvalidAmountsWithoutRounding() {
        for (String amount : new String[]{"0.00", "-1", "0.009", "10000000000000", "1e2"}) {
            assertNull(TransactionFormValidator.parseAmount(amount, '.'));
        }
    }

    @Test public void rejectsNonCalendarDates() {
        assertThrows(DateTimeParseException.class, () -> TransactionInput.date("2026-02-29"));
        assertThrows(DateTimeParseException.class, () -> TransactionInput.date("2026-2-01"));
    }
}
