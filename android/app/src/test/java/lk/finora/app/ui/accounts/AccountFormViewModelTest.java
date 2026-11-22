package lk.finora.app.ui.accounts;

import java.math.BigDecimal;
import java.io.IOException;

import org.junit.Test;

import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AccountFormViewModelTest {
    @Test
    public void acceptsSignedAndZeroBalancesExactly() {
        assertEquals(new BigDecimal("-9999999999999.99"),
                AccountFormViewModel.parseBalance("-9999999999999.99"));
        assertEquals(new BigDecimal("0.00"), AccountFormViewModel.parseBalance("0"));
    }

    @Test
    public void rejectsExcessPrecisionAndExponents() {
        assertThrows(IllegalArgumentException.class, () -> AccountFormViewModel.parseBalance("1.003"));
        assertThrows(IllegalArgumentException.class, () -> AccountFormViewModel.parseBalance("10000000000000"));
        assertThrows(IllegalArgumentException.class, () -> AccountFormViewModel.parseBalance("1e3"));
    }

    @Test
    public void dropsLateCallbacksAndRequiresReconciliationAfterAmbiguousCreate() {
        var client = new OkHttpClient();
        var request = new Request.Builder().url("http://localhost/").build();
        var active = client.newCall(request);
        assertFalse(Work.acceptsResult(false, active, active));
        assertFalse(Work.acceptsResult(true, active, client.newCall(request)));
        assertTrue(Work.acceptsResult(true, active, active));
        assertTrue(Work.isAmbiguousCreate(true, new IOException("Connection lost")));
        assertFalse(Work.isAmbiguousCreate(false, new IOException("Connection lost")));
    }
}
