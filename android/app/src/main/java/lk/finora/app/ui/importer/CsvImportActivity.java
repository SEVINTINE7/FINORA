package lk.finora.app.ui.importer;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.repo.CsvImportRepository.Row;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class CsvImportActivity extends SecureActivity {
    private CsvImportViewModel model;
    private Button choose;
    private Button refresh;
    private Button importRows;
    private TextView status;
    private TextView summary;
    private RowsAdapter rows;

    private final androidx.activity.result.ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && verify()) model.choose(getContentResolver(), uri);
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CsvImportViewModel.class);
        if (!verify()) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.finora_outer_margin);
        content.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText(R.string.sms_csv_import);
        title.setTextSize(24);
        ViewCompat.setAccessibilityHeading(title, true);
        content.addView(title);
        TextView instruction = new TextView(this);
        instruction.setText(R.string.csv_pick_help);
        content.addView(instruction);
        choose = button(content, R.string.csv_choose, () -> {
            if (verify() && !model.busy()) {
                picker.launch(new String[]{"text/csv", "text/comma-separated-values", "application/csv",
                        "application/vnd.ms-excel", "text/plain"});
            }
        });
        status = new TextView(this);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        summary = new TextView(this);
        content.addView(summary);
        rows = new RowsAdapter();
        ListView list = new ListView(this);
        list.setAdapter(rows);
        list.setSaveEnabled(false);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh = button(content, R.string.csv_refresh, () -> {
            if (verify()) model.refresh();
        });
        importRows = button(content, R.string.csv_review, this::review);
        setContentView(content);
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        verify();
    }

    private boolean verify() {
        return model != null && AccountListActivity.verify(this, model.work);
    }

    private void review() {
        if (!verify() || model.busy() || model.needsPreview || model.selectedCount() == 0) return;
        var reviewed = model.preview;
        var selected = model.reviewedLines();
        showSecure(new AlertDialog.Builder(this)
                .setMessage(getString(R.string.csv_confirm_message, selected.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.csv_confirm_action, (dialog, action) -> {
                    if (verify()) model.confirm(reviewed, selected);
                }).create());
    }

    private void render() {
        if (!verify()) return;
        boolean busy = model.busy();
        choose.setEnabled(!busy);
        refresh.setVisibility(model.canRefresh() ? View.VISIBLE : View.GONE);
        refresh.setEnabled(!busy);
        int message = busy ? R.string.records_loading : model.uncertain ? R.string.csv_uncertain
                : model.work.error;
        status.setVisibility(message != 0 || model.result != null ? View.VISIBLE : View.GONE);
        if (message != 0) {
            status.setText(message);
        } else if (model.result != null) {
            status.setText(getString(R.string.csv_result, model.result.saved(),
                    model.result.skippedDuplicateLines().size()));
        }
        summary.setVisibility(model.preview == null ? View.GONE : View.VISIBLE);
        if (model.preview != null) {
            summary.setText(getString(R.string.csv_summary, model.preview.totalRows(),
                    model.preview.errorRows(), model.preview.duplicateRows()));
        }
        rows.notifyDataSetChanged();
        showSelection();
    }

    private void showSelection() {
        int count = model.selectedCount();
        importRows.setVisibility(model.preview != null && !model.needsPreview ? View.VISIBLE : View.GONE);
        importRows.setEnabled(!model.busy() && count > 0);
        importRows.setText(getString(R.string.csv_review_count, count));
    }

    private Button button(LinearLayout parent, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private String describe(Row row) {
        String description = getString(R.string.csv_line, row.line());
        if (row.duplicate()) return getString(R.string.csv_line_duplicate, description);
        if (!row.selectable()) return getString(R.string.csv_line_invalid, description);
        int kind = "INCOME".equals(row.type()) ? R.string.category_income : R.string.category_expense;
        String text = description + "\n" + getString(R.string.transaction_row, row.txnDate(),
                getString(kind), row.amount().toPlainString());
        for (String field : new String[]{row.accountName(), row.categoryName(), row.merchant(), row.note()}) {
            if (field != null && !field.isEmpty()) text += "\n" + field;
        }
        return text;
    }

    private final class RowsAdapter extends BaseAdapter {
        @Override public int getCount() { return model.preview == null ? 0 : model.preview.rows().size(); }
        @Override public Row getItem(int position) { return model.preview.rows().get(position); }
        @Override public long getItemId(int position) { return getItem(position).line(); }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            var row = getItem(position);
            CheckBox item = recycled instanceof CheckBox checkbox ? checkbox : new CheckBox(CsvImportActivity.this);
            item.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            item.setOnCheckedChangeListener(null);
            item.setText(describe(row));
            item.setChecked(model.isSelected(row));
            item.setEnabled(row.selectable() && !model.busy() && !model.needsPreview);
            item.setOnCheckedChangeListener((button, checked) -> {
                model.select(row, checked);
                showSelection();
            });
            return item;
        }
    }
}
