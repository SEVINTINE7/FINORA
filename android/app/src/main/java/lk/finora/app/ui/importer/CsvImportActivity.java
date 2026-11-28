package lk.finora.app.ui.importer;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import lk.finora.app.R;
import lk.finora.app.data.repo.CsvImportRepository.Row;
import lk.finora.app.databinding.ActivityCsvImportBinding;
import lk.finora.app.databinding.ItemImportTxnBinding;
import lk.finora.app.databinding.ViewCsvHeaderBinding;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.sms.ImportRows;

/** T67.3/D65/D71 CSV import: file card, summary tiles, preview rows to tick, and the pinned import button. */
public final class CsvImportActivity extends SecureActivity {
    private CsvImportViewModel model;
    private ActivityCsvImportBinding view;
    private ViewCsvHeaderBinding header;
    private final Rows rows = new Rows();

    private final androidx.activity.result.ActivityResultLauncher<String[]> picker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && verify()) model.choose(getContentResolver(), uri);
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CsvImportViewModel.class);
        if (!verify()) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityCsvImportBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        view.csvList.setLayoutManager(new LinearLayoutManager(this));
        header = ViewCsvHeaderBinding.inflate(getLayoutInflater(), view.csvList, false);
        ViewCompat.setAccessibilityHeading(header.csvHeading, true);
        ViewCompat.setAccessibilityHeading(header.csvPreviewHeading, true);
        header.csvChoose.setOnClickListener(ignored -> {
            if (verify() && !model.busy()) {
                picker.launch(new String[]{"text/csv", "text/comma-separated-values", "application/csv",
                        "application/vnd.ms-excel", "text/plain"});
            }
        });
        header.csvRefresh.setOnClickListener(ignored -> {
            if (verify()) model.refresh();
        });
        tile(header.csvReady.getRoot(), R.string.csv_ready, R.color.finora_success_surface, R.color.finora_green);
        tile(header.csvFix.getRoot(), R.string.csv_fix, R.color.finora_error_surface, R.color.finora_error);
        tile(header.csvDone.getRoot(), R.string.csv_done, R.color.finora_neutral_surface, R.color.finora_ink);
        ((ViewGroup.MarginLayoutParams) header.csvDone.getRoot().getLayoutParams()).setMarginEnd(0);
        view.csvList.setAdapter(rows);
        view.csvImport.setOnClickListener(ignored -> review());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        verify();
    }

    private boolean verify() {
        return model != null && AccountListActivity.verify(this, model.work);
    }

    private void tile(View tile, int label, int fill, int ink) {
        tile.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        ((TextView) tile.findViewById(R.id.tile_label)).setText(label);
        ((TextView) tile.findViewById(R.id.tile_label)).setTextColor(getColor(ink));
        ((TextView) tile.findViewById(R.id.tile_value)).setTextColor(getColor(ink));
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
        var preview = model.preview;
        header.csvFileName.setText(model.fileName != null ? model.fileName : getString(R.string.csv_file_none));
        header.csvFileMeta.setText(preview != null
                ? getResources().getQuantityString(R.plurals.csv_rows_checked, preview.totalRows(), preview.totalRows())
                : getString(R.string.csv_pick_help));
        header.csvChoose.setText(model.fileName != null ? R.string.csv_choose_other : R.string.csv_choose);
        header.csvChoose.setEnabled(!busy);
        header.csvRefresh.setVisibility(model.canRefresh() ? View.VISIBLE : View.GONE);
        header.csvRefresh.setEnabled(!busy);
        int message = busy ? R.string.records_loading : model.uncertain ? R.string.csv_uncertain : model.work.error;
        if (message != 0) {
            header.csvStatus.setText(message);
        } else if (model.result != null) {
            header.csvStatus.setText(getString(R.string.csv_result, model.result.saved(),
                    model.result.skippedDuplicateLines().size()));
        }
        header.csvStatus.setVisibility(message != 0 || model.result != null ? View.VISIBLE : View.GONE);
        header.csvSummary.setVisibility(preview == null ? View.GONE : View.VISIBLE);
        header.csvPreviewHeader.setVisibility(preview == null || preview.rows().isEmpty() ? View.GONE : View.VISIBLE);
        if (preview != null) {
            ((TextView) header.csvReady.getRoot().findViewById(R.id.tile_value)).setText(String.valueOf(preview.validRows()));
            ((TextView) header.csvFix.getRoot().findViewById(R.id.tile_value)).setText(String.valueOf(preview.errorRows()));
            ((TextView) header.csvDone.getRoot().findViewById(R.id.tile_value)).setText(String.valueOf(preview.duplicateRows()));
            header.csvSummary.setContentDescription(getString(R.string.csv_summary_spoken,
                    preview.validRows(), preview.errorRows(), preview.duplicateRows()));
        }
        rows.show(preview == null ? null : preview.rows());
        showSelection();
    }

    private void showSelection() {
        int count = model.selectedCount();
        view.csvBar.setVisibility(model.preview != null && !model.needsPreview ? View.VISIBLE : View.GONE);
        view.csvFixNote.setVisibility(model.preview != null && model.preview.errorRows() > 0 ? View.VISIBLE : View.GONE);
        view.csvImport.setEnabled(!model.busy() && count > 0);
        view.csvImport.setText(getResources().getQuantityString(R.plurals.csv_import_count, count, count));
    }

    private String date(String text) {
        if (text == null) return "";
        try {
            return ImportRows.date(this, LocalDate.parse(text));
        } catch (DateTimeParseException invalid) {
            return text;
        }
    }

    private void bind(ItemImportTxnBinding row, Row item, int position, int count) {
        boolean income = "INCOME".equals(item.type());
        String title = item.merchant() != null && !item.merchant().isEmpty() ? item.merchant()
                : item.categoryName() != null && !item.categoryName().isEmpty() ? item.categoryName()
                : getString(R.string.csv_no_merchant);
        var details = new ArrayList<String>();
        details.add(date(item.txnDate()));
        if (item.accountName() != null && !item.accountName().isEmpty()) details.add(item.accountName());
        if (item.categoryName() != null && !item.categoryName().isEmpty() && !title.equals(item.categoryName())) {
            details.add(item.categoryName());
        }
        String meta = getString(R.string.csv_row_meta, item.line(), String.join(" · ", details));
        ImportRows.bind(row, this, income, title, meta, item.amount() == null ? BigDecimal.ZERO : item.amount());
        if (item.amount() == null) row.importAmount.setText("");
        int background = count == 1 ? R.drawable.finora_row_single : position == 0 ? R.drawable.finora_row_top
                : position == count - 1 ? R.drawable.finora_row_bottom : R.drawable.finora_row_middle;
        row.getRoot().setBackgroundResource(background);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        row.getRoot().setPaddingRelative(pad, row.getRoot().getPaddingTop(), pad, row.getRoot().getPaddingBottom());
        boolean selectable = item.selectable();
        boolean enabled = selectable && !model.busy() && !model.needsPreview;
        row.importCheck.setOnCheckedChangeListener(null);
        row.importCheck.setVisibility(selectable ? View.VISIBLE : View.INVISIBLE);
        row.importCheck.setChecked(model.isSelected(item));
        row.importCheck.setEnabled(enabled);
        row.importCheck.setContentDescription(meta);
        row.importCheck.setOnCheckedChangeListener((button, checked) -> {
            model.select(item, checked);
            showSelection();
        });
        row.getRoot().setOnClickListener(enabled ? ignored -> row.importCheck.toggle() : null);
        row.getRoot().setClickable(enabled);
        row.getRoot().setAlpha(selectable ? 1f : 0.8f);
        String note = null;
        int noteColor = R.color.finora_muted;
        int noteIcon = R.drawable.finora_ic_clock;
        if (item.duplicate()) {
            note = getString(R.string.csv_done);
        } else if (!selectable) {
            note = item.errors() == null || item.errors().isEmpty() ? getString(R.string.csv_row_fix)
                    : String.join("; ", item.errors());
            noteColor = R.color.finora_error;
            noteIcon = R.drawable.finora_ic_info;
        }
        row.importNote.setVisibility(note == null ? View.GONE : View.VISIBLE);
        if (note != null) {
            row.importNote.setText(note);
            row.importNote.setTextColor(getColor(noteColor));
            row.importNote.setCompoundDrawablesRelativeWithIntrinsicBounds(noteIcon, 0, 0, 0);
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(row.importNote,
                    ColorStateList.valueOf(getColor(noteColor)));
            var icon = row.importNote.getCompoundDrawablesRelative()[0];
            int size = Math.round(14 * getResources().getDisplayMetrics().density);
            if (icon != null) icon.setBounds(0, 0, size, size);
            row.importNote.setCompoundDrawablesRelative(icon, null, null, null);
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ItemImportTxnBinding row;
        Holder(View view, ItemImportTxnBinding row) {
            super(view);
            this.row = row;
        }
    }

    /** Item 0 is the header; the rest are preview rows. */
    private final class Rows extends RecyclerView.Adapter<Holder> {
        private java.util.List<Row> items = java.util.List.of();

        @android.annotation.SuppressLint("NotifyDataSetChanged") // the whole preview is replaced at once
        void show(java.util.List<Row> next) {
            items = next == null ? java.util.List.of() : next;
            notifyDataSetChanged();
        }

        @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }
        @Override public int getItemCount() { return items.size() + 1; }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 0) {
                if (header.getRoot().getParent() instanceof ViewGroup old) old.removeView(header.getRoot());
                return new Holder(header.getRoot(), null);
            }
            var row = ItemImportTxnBinding.inflate(getLayoutInflater(), parent, false);
            return new Holder(row.getRoot(), row);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            if (holder.row != null) bind(holder.row, items.get(position - 1), position - 1, items.size());
        }
    }
}
