package lk.finora.app.ui.accounts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class AccountListActivity extends AppCompatActivity {
    public static final String ITEM_ID = "record_id";
    private AccountListViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AccountListViewModel.class);
        if (!verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_account_list);
        findViewById(R.id.records_add).setOnClickListener(view -> open(0));
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (verify(this, model.work)) model.load();
    }

    private void render() {
        if (!verify(this, model.work)) return;
        showListState(this, model.work, model.rows.isEmpty());
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        for (var item : model.rows) {
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
        status.setText(message == 0 ? "" : activity.getString(message));
        activity.findViewById(R.id.records_add).setEnabled(!work.busy());
        activity.findViewById(R.id.records_retry).setEnabled(!work.busy());
    }

    public static void confirm(AppCompatActivity activity, Work work, @StringRes int message, Runnable action) {
        if (!verify(activity, work) || work.busy()) return;
        new AlertDialog.Builder(activity).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.records_remove, (dialog, which) -> {
                    if (verify(activity, work)) action.run();
                }).show();
    }
}
