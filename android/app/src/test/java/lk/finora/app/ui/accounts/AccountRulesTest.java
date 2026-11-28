package lk.finora.app.ui.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;

public final class AccountRulesTest {
    private static AccountDto account(long id, String type, boolean archived) {
        return new AccountDto(id, "Account " + id, type, new BigDecimal("0.00"), archived);
    }

    @Test public void archivedAccountsMoveToTheirOwnSectionKeepingOrder() {
        var rows = List.of(account(1, "BANK", false), account(2, "CASH", true), account(3, "CARD", false),
                account(4, "WALLET", true));
        assertEquals(List.of(1L, 3L), AccountRules.inUse(rows).stream().map(AccountDto::id).toList());
        assertEquals(List.of(2L, 4L), AccountRules.archived(rows).stream().map(AccountDto::id).toList());
        assertEquals(List.of(), AccountRules.archived(List.of(account(5, "BANK", false))));
    }

    @Test public void eachTypeHasItsLabelAndIcon() {
        assertEquals(0, AccountRules.typeIndex("BANK"));
        assertEquals(3, AccountRules.typeIndex("WALLET"));
        assertEquals(-1, AccountRules.typeIndex("LOAN"));
        assertEquals(R.string.account_card, AccountRules.LABELS[AccountRules.typeIndex("CARD")]);
        assertEquals(R.drawable.finora_ic_bank, AccountRules.icon("BANK"));
        assertEquals(R.drawable.finora_ic_cash, AccountRules.icon("CASH"));
        assertEquals(R.drawable.finora_ic_cat_card, AccountRules.icon("CARD"));
        assertEquals(R.drawable.finora_ic_wallet, AccountRules.icon("LOAN"));
    }
}
