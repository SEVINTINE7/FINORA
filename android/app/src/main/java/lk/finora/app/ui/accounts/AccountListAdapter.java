package lk.finora.app.ui.accounts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import lk.finora.app.R;
import lk.finora.app.ui.FinancialCard;
import lk.finora.app.data.api.AccountDto;

final class AccountListAdapter {
    private AccountListAdapter() {}

    static View row(Context context, ViewGroup parent, AccountDto item, Runnable edit, Runnable remove) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_account, parent, false);
        int type = java.util.Arrays.asList(AccountFormViewModel.TYPES).indexOf(item.type());
        int[] labels = {R.string.account_bank, R.string.account_cash, R.string.account_card, R.string.account_wallet};
        String typeLabel = type < 0 ? "" : context.getString(labels[type]);
        String detail = context.getString(R.string.account_summary, typeLabel,
                item.openingBalance().toPlainString());
        if (item.archived()) detail += " · " + context.getString(R.string.account_archived);
        ((TextView) row.findViewById(R.id.record_name)).setText(item.name());
        ((TextView) row.findViewById(R.id.record_details)).setText(detail);
        View editButton = row.findViewById(R.id.record_edit);
        View removeButton = row.findViewById(R.id.record_remove);
        editButton.setContentDescription(context.getString(R.string.records_edit_named, item.name()));
        removeButton.setContentDescription(context.getString(R.string.records_remove_named, item.name()));
        editButton.setOnClickListener(view -> edit.run());
        removeButton.setOnClickListener(view -> remove.run());
        return FinancialCard.wrap((android.widget.LinearLayout) row, item.name(),
                R.drawable.finora_nav_records, editButton, removeButton);
    }
}
