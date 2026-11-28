package lk.finora.app.ui.accounts;

import java.util.List;
import java.util.stream.Collectors;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;

/** T65.2/D69: pure account list rules, kept out of the activity for unit tests. */
final class AccountRules {
    private AccountRules() {}

    /** Accounts in use, in API order. */
    static List<AccountDto> inUse(List<AccountDto> rows) {
        return rows.stream().filter(row -> !row.archived()).collect(Collectors.toList());
    }

    /** Archived accounts, in API order; shown in their own section without a remove button. */
    static List<AccountDto> archived(List<AccountDto> rows) {
        return rows.stream().filter(AccountDto::archived).collect(Collectors.toList());
    }

    /** Position of the type in the form's choices (Bank, Cash, Card, Wallet), or -1 when unknown. */
    static int typeIndex(String type) {
        return java.util.Arrays.asList(AccountFormViewModel.TYPES).indexOf(type);
    }

    static final int[] LABELS = {R.string.account_bank, R.string.account_cash, R.string.account_card,
            R.string.account_wallet};
    static final int[] ICONS = {R.drawable.finora_ic_bank, R.drawable.finora_ic_cash, R.drawable.finora_ic_cat_card,
            R.drawable.finora_ic_wallet};

    static int icon(String type) {
        int index = typeIndex(type);
        return index < 0 ? R.drawable.finora_ic_wallet : ICONS[index];
    }
}
