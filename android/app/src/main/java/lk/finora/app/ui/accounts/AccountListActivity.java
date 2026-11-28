package lk.finora.app.ui.accounts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityAccountListBinding;
import lk.finora.app.ui.FinancialCard;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class AccountListActivity extends SecureActivity {
    public static final String ITEM_ID = "record_id";
    private AccountListViewModel model;
    private ActivityAccountListBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AccountListViewModel.class);
        if (!verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityAccountListBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.accountHeading, true);
        ViewCompat.setAccessibilityHeading(view.accountInUseHeading, true);
        ViewCompat.setAccessibilityHeading(view.accountArchivedHeading, true);
        view.recordsAdd.setOnClickListener(ignored -> open(0));
        view.recordsRetry.setOnClickListener(ignored -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (verify(this, model.work)) model.load();
    }

    private void render() {
        if (!verify(this, model.work)) return;
        showListState(this, model.work, model.rows.isEmpty());
        boolean failed = !model.work.busy() && model.work.error != 0;
        view.recordsRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        var inUse = AccountRules.inUse(model.rows);
        var archived = AccountRules.archived(model.rows);
        view.accountInUseHeader.setVisibility(inUse.isEmpty() ? View.GONE : View.VISIBLE);
        view.accountInUseCount.setText(getResources().getQuantityString(R.plurals.account_count, inUse.size(), inUse.size()));
        view.accountArchivedHeader.setVisibility(archived.isEmpty() ? View.GONE : View.VISIBLE);
        view.accountArchivedNote.setVisibility(archived.isEmpty() ? View.GONE : View.VISIBLE);
        view.accountArchivedCount.setText(getResources().getQuantityString(R.plurals.account_count,
                archived.size(), archived.size()));
        fill(view.recordsRows, inUse);
        fill(view.accountArchivedRows, archived);
    }

    private void fill(LinearLayout rows, java.util.List<lk.finora.app.data.api.AccountDto> items) {
        rows.removeAllViews();
        for (var item : items) {
            rows.addView(AccountListAdapter.row(this, rows, item, () -> open(item.id()),
                    () -> confirm(this, model.work, R.string.account_remove_message,
                        () -> model.delete(item.id()))));
        }
    }

    private void open(long id) {
        if (verify(this, model.work) && !model.work.busy()) {
            startActivity(new Intent(this, AccountFormActivity.class).putExtra(ITEM_ID, id));
        }
    }

    public static boolean verify(AppCompatActivity activity, Work work) {
        if (activity.isFinishing() || !work.isCurrent()) {
            activity.finish();
            return false;
        }
        return true;
    }

    public static void showListState(AppCompatActivity activity, Work work, boolean empty) {
        TextView status = activity.findViewById(R.id.records_status);
        int message = work.busy() ? R.string.records_loading : work.error != 0 ? work.error
                : empty ? R.string.records_empty : 0;
        FinancialCard.showState(status, message, !work.busy() && work.error != 0);
        activity.findViewById(R.id.records_add).setEnabled(!work.busy());
        activity.findViewById(R.id.records_retry).setEnabled(!work.busy());
    }

    public static void confirm(AppCompatActivity activity, Work work, @StringRes int message, Runnable action) {
        if (!verify(activity, work) || work.busy()) return;
        showSecure(new AlertDialog.Builder(activity).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.records_remove, (dialog, which) -> {
                    if (verify(activity, work)) action.run();
                }).create());
    }
}
