package lk.finora.app.ui.transactions;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Stand-alone Records screen (review after import, deep links); the Records tab hosts the same panel (T62.4). */
public final class TransactionActivity extends SecureActivity {
    static final String REVIEW_CREATE = "review_uncertain_create";
    private TransactionViewModel model;
    private TransactionListPanel panel;

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
        model = new ViewModelProvider(this).get(TransactionViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        boolean review = getIntent().getBooleanExtra(REVIEW_CREATE, false);
        panel = new TransactionListPanel(this, model, review, () -> {
            setResult(RESULT_OK);
            finish();
        });
        panel.heading(R.string.transaction_title);
        setContentView(panel.view());
        model.start();
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }
}
