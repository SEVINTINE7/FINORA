package lk.finora.app.ui.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.ui.transactions.TransactionFormValidator.Draft;
import lk.finora.app.ui.transactions.TransactionFormValidator.Field;

public class TransactionFormTest {
    private final List<AccountDto> accounts = List.of(
            new AccountDto(1, "Cash", "CASH", BigDecimal.ZERO, false),
            new AccountDto(2, "Old cash", "CASH", BigDecimal.ZERO, true));
    private final List<CategoryDto> categories = List.of(
            new CategoryDto(3, "Food", null, "EXPENSE", null, true));

    @Test public void acceptsExactLocalizedMoneyAndOriginalArchivedAccountOnly() {
        var draft = new Draft(2L, 3L, "EXPENSE", "9999999999999,09", "2026-11-23", "", "");
        var valid = TransactionFormValidator.validate(draft, ',', accounts, categories, 2L);
        assertTrue(valid.errors().isEmpty());
        assertEquals(new BigDecimal("9999999999999.09"), valid.request().amount());
        assertNull(valid.request().merchant());
        assertTrue(TransactionFormValidator.validate(draft, ',', accounts, categories, null)
                .errors().containsKey(Field.ACCOUNT));
    }

    @Test public void rejectsIncompleteAndMismatchedDraftWithoutRounding() {
        var draft = new Draft(null, 3L, null, "0.001", "", "m".repeat(101), "n".repeat(256));
        var invalid = TransactionFormValidator.validate(draft, '.', accounts, categories, null);
        assertEquals(7, invalid.errors().size());
        assertNull(invalid.request());
        assertNull(TransactionFormValidator.parseAmount("1.000,01", ','));
        assertNull(TransactionFormValidator.parseAmount("1e2", '.'));
        assertNull(TransactionFormValidator.parseAmount("0", '.'));
        assertEquals(new BigDecimal("1.00"), TransactionFormValidator.parseAmount("1", '.'));
    }

    @Test public void uncertainCreateRequiresRefreshedRecordsAndExplicitConfirmationForEachAttempt() {
        var retry = new TransactionFormViewModel.CreateRetry();
        assertTrue(retry.permits(false));
        assertTrue(TransactionFormViewModel.isUncertainCreate(new IOException("connection ended")));
        retry.markUncertain();
        assertFalse(retry.permits(false));
        assertFalse(retry.permits(true));
        retry.reviewed = true;
        assertFalse(retry.permits(false));
        assertTrue(retry.permits(true));
        retry.markUncertain();
        assertFalse(retry.permits(true));
    }
}
