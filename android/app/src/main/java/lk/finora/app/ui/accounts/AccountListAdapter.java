package lk.finora.app.ui.accounts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.databinding.ItemAccountBinding;
import lk.finora.app.ui.Money;

/** T65.2/D69 account card: tap to edit, type icon, opening balance, Archived chip or a remove button. */
final class AccountListAdapter {
    private AccountListAdapter() {}

    static View row(Context context, ViewGroup parent, AccountDto item, Runnable edit, Runnable remove) {
        var card = ItemAccountBinding.inflate(LayoutInflater.from(context), parent, false);
        int type = AccountRules.typeIndex(item.type());
        String typeLabel = type < 0 ? "" : context.getString(AccountRules.LABELS[type]);
        card.recordName.setText(item.name());
        card.recordDetails.setText(context.getString(R.string.account_row, typeLabel,
                Money.rupees(context, item.openingBalance())));
        card.accountIcon.setImageResource(AccountRules.icon(item.type()));
        card.accountIcon.setBackgroundTintList(ColorStateList.valueOf(context.getColor(item.archived()
                ? R.color.finora_neutral_surface : R.color.finora_success_surface)));
        card.accountIcon.setImageTintList(ColorStateList.valueOf(context.getColor(item.archived()
                ? R.color.finora_muted : R.color.finora_green)));
        card.accountArchived.setVisibility(item.archived() ? View.VISIBLE : View.GONE);
        // Removing an archived account only archives it again, and the API has no unarchive.
        card.recordRemove.setVisibility(item.archived() ? View.GONE : View.VISIBLE);
        if (item.archived()) {
            card.accountOpen.setPaddingRelative(card.accountOpen.getPaddingStart(), card.accountOpen.getPaddingTop(),
                    Math.round(14 * context.getResources().getDisplayMetrics().density), card.accountOpen.getPaddingBottom());
        }
        card.recordRemove.setContentDescription(context.getString(R.string.records_remove_named, item.name()));
        card.recordRemove.setOnClickListener(view -> remove.run());

        // TalkBack: name and the earlier exact LKR summary, the archived state, and "Edit <name>" as the action.
        String spoken = context.getString(R.string.account_summary, typeLabel, item.openingBalance().toPlainString());
        card.accountOpen.setContentDescription(item.name() + ". " + spoken);
        if (item.archived()) ViewCompat.setStateDescription(card.accountOpen, context.getString(R.string.account_archived));
        ViewCompat.replaceAccessibilityAction(card.accountOpen, AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.records_edit_named, item.name()), null);
        card.accountOpen.setOnClickListener(view -> edit.run());
        return card.getRoot();
    }
}
