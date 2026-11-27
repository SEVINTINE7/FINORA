package lk.finora.app.ui.transactions;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lk.finora.app.R;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.databinding.ItemRecordsDayBinding;
import lk.finora.app.databinding.ItemTransactionRowBinding;
import lk.finora.app.databinding.PanelRecordsBinding;
import lk.finora.app.ui.FinancialCard;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.importer.CsvImportActivity;
import lk.finora.app.ui.sms.SmsAccessActivity;

/**
 * T62.4/D65 Records list, hosted by the Records tab and by {@link TransactionActivity}. Tap a row to edit;
 * swipe it, long-press it or use the accessibility action to remove it after confirmation (no undo).
 */
public final class TransactionListPanel {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private final AppCompatActivity activity;
    private final TransactionViewModel model;
    private final PanelRecordsBinding view;
    private final boolean review;
    private final Adapter adapter = new Adapter();
    private final ActivityResultLauncher<Intent> editForm;

    /** Call from the host's onCreate, after {@link FinancialDatePicker#protect} ran before super.onCreate. */
    public TransactionListPanel(AppCompatActivity activity, TransactionViewModel model, boolean review,
                                Runnable reviewed) {
        this.activity = activity;
        this.model = model;
        this.review = review;
        editForm = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (verify() && result.getResultCode() == AppCompatActivity.RESULT_OK) model.reload();
        });
        view = PanelRecordsBinding.inflate(LayoutInflater.from(activity));
        ViewCompat.setAccessibilityHeading(view.recordsHeading, true);
        view.recordsReviewBanner.setVisibility(review ? View.VISIBLE : View.GONE);
        view.recordsReviewDone.setOnClickListener(ignored -> {
            if (verify() && model.canFinishReview()) reviewed.run();
        });
        view.recordsImport.setVisibility(review ? View.GONE : View.VISIBLE);
        view.recordsImport.setOnClickListener(ignored -> chooseImport());
        view.recordsRetry.setOnClickListener(ignored -> {
            if (verify()) model.retry();
        });
        view.recordsType.check(typeButton(model.filters.type()));
        view.recordsType.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || !verify()) return;
            String type = id == R.id.records_type_income ? "INCOME" : id == R.id.records_type_expense ? "EXPENSE" : null;
            if (Objects.equals(type, model.filters.type())) return;
            var current = model.filters;
            Long category = current.categoryId();
            var chosen = TransactionRows.category(model.categories, category);
            if (type != null && chosen != null && !type.equals(chosen.kind())) category = null;
            model.filter(new Filters(current.from(), current.to(), type, category, current.accountId()));
        });
        view.recordsFilterDates.setOnClickListener(ignored -> chooseDates());
        view.recordsFilterAccount.setOnClickListener(ignored -> chooseAccount());
        view.recordsFilterCategory.setOnClickListener(ignored -> chooseCategory());
        FinancialDatePicker.restoreRange(activity, this::applyDates);
        view.recordsList.setLayoutManager(new LinearLayoutManager(activity));
        view.recordsList.setAdapter(adapter);
        if (!review) new ItemTouchHelper(new SwipeToRemove()).attachToRecyclerView(view.recordsList);
        model.work.changes.observe(activity, ignored -> render());
    }

    public View view() { return view.getRoot(); }

    public void heading(int text) { view.recordsHeading.setText(text); }

    /** Load on first display; later displays refresh rows changed on other screens. */
    public void show() {
        if (!verify()) return;
        if (model.ready) model.reload();
        else model.start();
    }

    private boolean verify() { return AccountListActivity.verify(activity, model.work); }

    private Context context() { return activity; }

    private void render() {
        if (!verify()) return;
        boolean busy = model.work.busy();
        boolean failed = !busy && model.work.error != 0;
        var items = model.pagination.items();
        int message = busy && items.isEmpty() ? R.string.records_loading : failed ? model.work.error
                : !busy && model.pagination.hasCurrentResult() && items.isEmpty() ? R.string.records_empty : 0;
        FinancialCard.showState(view.recordsStatus, message, failed);
        view.recordsRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        boolean controls = model.ready && !busy;
        for (View control : new View[]{view.recordsTypeAll, view.recordsTypeExpense, view.recordsTypeIncome,
                view.recordsFilterDates, view.recordsFilterAccount, view.recordsFilterCategory}) {
            control.setEnabled(controls);
        }
        view.recordsReviewDone.setEnabled(model.canFinishReview());
        showFilters();
        adapter.update(items, model.pagination.hasMore());
    }

    private void showFilters() {
        var filters = model.filters;
        boolean dated = filters.from() != null && filters.to() != null;
        chip(view.recordsFilterDates, dated, dated ? dateRange(filters.from(), filters.to())
                : context().getString(R.string.records_all_dates), this::clearDates);
        var account = filters.accountId() == null ? null : TransactionRows.account(model.accounts, filters.accountId());
        chip(view.recordsFilterAccount, account != null, account != null ? account.name()
                : context().getString(R.string.transaction_account), () -> applyAccount(null));
        var category = TransactionRows.category(model.categories, filters.categoryId());
        chip(view.recordsFilterCategory, category != null, category != null
                ? TransactionRows.displayName(context(), category)
                : context().getString(R.string.transaction_category), () -> applyCategory(null));
    }

    private void chip(com.google.android.material.chip.Chip chip, boolean active, String text, Runnable clear) {
        chip.setText(text);
        chip.setChecked(active);
        chip.setCloseIconResource(active ? R.drawable.finora_ic_close : R.drawable.finora_ic_chevron_down);
        chip.setCloseIconContentDescription(active ? context().getString(R.string.records_clear_filter, text) : null);
        chip.setOnCloseIconClickListener(active ? ignored -> {
            if (verify() && !model.work.busy()) clear.run();
        } : ignored -> chip.performClick());
    }

    private String dateRange(String from, String to) {
        var format = DateTimeFormatter.ofPattern("d MMM", locale());
        return context().getString(R.string.records_date_range,
                LocalDate.parse(from).format(format), LocalDate.parse(to).format(format));
    }

    private void chooseDates() {
        if (!verify() || model.work.busy()) {
            showFilters();
            return;
        }
        var filters = model.filters;
        FinancialDatePicker.showRange(activity, R.string.records_choose_dates,
                filters.from() == null ? null : LocalDate.parse(filters.from()),
                filters.to() == null ? null : LocalDate.parse(filters.to()), this::applyDates);
        showFilters();
    }

    private void applyDates(LocalDate from, LocalDate to) {
        if (!verify()) return;
        var current = model.filters;
        model.filter(new Filters(from.toString(), to.toString(), current.type(), current.categoryId(), current.accountId()));
    }

    private void clearDates() {
        var current = model.filters;
        model.filter(new Filters(null, null, current.type(), current.categoryId(), current.accountId()));
    }

    private void chooseAccount() {
        showFilters();
        if (!verify() || model.work.busy()) return;
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        labels.add(context().getString(R.string.transaction_all));
        values.add(null);
        for (var account : model.accounts) {
            labels.add(account.archived() ? context().getString(R.string.transaction_archived_name, account.name())
                    : account.name());
            values.add(account.id());
        }
        choose(R.string.transaction_account, labels, values.indexOf(model.filters.accountId()),
                index -> applyAccount(values.get(index)));
    }

    private void applyAccount(Long account) {
        var current = model.filters;
        model.filter(new Filters(current.from(), current.to(), current.type(), current.categoryId(), account));
    }

    private void chooseCategory() {
        showFilters();
        if (!verify() || model.work.busy()) return;
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        labels.add(context().getString(R.string.transaction_all));
        values.add(null);
        String type = model.filters.type();
        for (var category : model.categories) {
            if (type != null && !type.equals(category.kind())) continue;
            labels.add(TransactionRows.displayName(context(), category));
            values.add(category.id());
        }
        choose(R.string.transaction_category, labels, values.indexOf(model.filters.categoryId()),
                index -> applyCategory(values.get(index)));
    }

    private void applyCategory(Long category) {
        var current = model.filters;
        model.filter(new Filters(current.from(), current.to(), current.type(), category, current.accountId()));
    }

    private void choose(int title, List<String> labels, int selected, java.util.function.IntConsumer pick) {
        SecureActivity.showSecure(new AlertDialog.Builder(activity).setTitle(title)
                .setSingleChoiceItems(labels.toArray(new String[0]), Math.max(0, selected), (dialog, which) -> {
                    dialog.dismiss();
                    if (verify() && !model.work.busy()) pick.accept(which);
                })
                .setNegativeButton(android.R.string.cancel, null).create());
    }

    private void chooseImport() {
        if (!verify()) return;
        String[] labels = {context().getString(R.string.sms_csv_import), context().getString(R.string.sms_access_title)};
        SecureActivity.showSecure(new AlertDialog.Builder(activity).setTitle(R.string.records_import)
                .setItems(labels, (dialog, which) -> {
                    if (!verify()) return;
                    activity.startActivity(new Intent(activity, which == 0 ? CsvImportActivity.class : SmsAccessActivity.class));
                })
                .setNegativeButton(android.R.string.cancel, null).create());
    }

    private void edit(Row row) {
        if (review || !verify() || model.work.busy()) return;
        editForm.launch(new Intent(activity, TransactionFormActivity.class).putExtra(TransactionFormActivity.ID, row.id()));
    }

    private void confirmRemove(Row row, Runnable restore) {
        if (review || !verify() || model.work.busy()) {
            restore.run();
            return;
        }
        var dialog = new AlertDialog.Builder(activity).setMessage(R.string.transaction_remove)
                .setNegativeButton(android.R.string.cancel, (shown, which) -> restore.run())
                .setPositiveButton(R.string.records_remove, (shown, which) -> {
                    if (verify()) model.delete(row.id());
                    else restore.run();
                })
                .setOnCancelListener(shown -> restore.run()).create();
        SecureActivity.showSecure(dialog);
    }

    private int typeButton(String type) {
        return "INCOME".equals(type) ? R.id.records_type_income
                : "EXPENSE".equals(type) ? R.id.records_type_expense : R.id.records_type_all;
    }

    private Locale locale() { return context().getResources().getConfiguration().getLocales().get(0); }

    private int dp(int value) {
        return Math.round(value * context().getResources().getDisplayMetrics().density);
    }

    /** List entries: a day header, a transaction row, or the Load more footer. */
    private record Entry(int kind, String day, String total, Row row, TransactionRows.Position position) {
        static final int DAY = 0;
        static final int ROW = 1;
        static final int MORE = 2;
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Entry> entries = new ArrayList<>();

        // Day groups, positions and totals are rebuilt together, so every entry can change.
        @android.annotation.SuppressLint("NotifyDataSetChanged")
        void update(List<Row> rows, boolean more) {
            entries.clear();
            LocalDate today = LocalDate.now(COLOMBO);
            int start = 0;
            while (start < rows.size()) {
                String date = rows.get(start).txnDate();
                int end = start;
                BigDecimal total = BigDecimal.ZERO;
                while (end < rows.size() && date.equals(rows.get(end).txnDate())) {
                    var row = rows.get(end);
                    total = "INCOME".equals(row.type()) ? total.add(row.amount()) : total.subtract(row.amount());
                    end++;
                }
                // The last day may continue on the next page, so its total would be partial.
                boolean complete = end < rows.size() || !more;
                entries.add(new Entry(Entry.DAY, TransactionRows.day(context(), date, today),
                        complete ? Money.net(context(), total) : "", null, null));
                for (int i = start; i < end; i++) {
                    entries.add(new Entry(Entry.ROW, null, null, rows.get(i),
                            TransactionRows.position(i - start, end - start)));
                }
                start = end;
            }
            if (more) entries.add(new Entry(Entry.MORE, null, null, null, null));
            notifyDataSetChanged();
        }

        Entry entry(int position) { return entries.get(position); }

        @Override public int getItemCount() { return entries.size(); }

        @Override public int getItemViewType(int position) { return entries.get(position).kind(); }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int kind) {
            var inflater = LayoutInflater.from(parent.getContext());
            if (kind == Entry.DAY) return new DayHolder(ItemRecordsDayBinding.inflate(inflater, parent, false));
            if (kind == Entry.ROW) return new RowHolder(ItemTransactionRowBinding.inflate(inflater, parent, false));
            View more = inflater.inflate(R.layout.item_records_more, parent, false);
            more.setOnClickListener(ignored -> {
                if (verify()) model.loadMore();
            });
            return new RecyclerView.ViewHolder(more) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Entry entry = entries.get(position);
            if (holder instanceof DayHolder day) {
                day.view.dayLabel.setText(entry.day());
                day.view.dayTotal.setText(entry.total());
                ViewCompat.setAccessibilityHeading(day.view.dayLabel, true);
            } else if (holder instanceof RowHolder rowHolder) {
                rowHolder.bind(entry.row(), entry.position());
            } else {
                holder.itemView.setEnabled(!model.work.busy());
            }
        }
    }

    private static final class DayHolder extends RecyclerView.ViewHolder {
        final ItemRecordsDayBinding view;

        DayHolder(ItemRecordsDayBinding view) {
            super(view.getRoot());
            this.view = view;
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        final ItemTransactionRowBinding view;
        private int removeAction = View.NO_ID;

        RowHolder(ItemTransactionRowBinding view) {
            super(view.getRoot());
            this.view = view;
        }

        void bind(Row row, TransactionRows.Position position) {
            var category = TransactionRows.category(model.categories, row.categoryId());
            var account = TransactionRows.account(model.accounts, row.accountId());
            String categoryName = category == null ? context().getString(R.string.dashboard_uncategorized)
                    : TransactionRows.displayName(context(), category);
            String meta = account == null ? categoryName : categoryName + " · " + account.name();
            TransactionRows.bind(view, row, category, meta, position);
            var root = view.getRoot();
            if (removeAction != View.NO_ID) ViewCompat.removeAccessibilityAction(root, removeAction);
            removeAction = View.NO_ID;
            if (review) {
                root.setClickable(false);
                root.setLongClickable(false);
                return;
            }
            String name = view.rowTitle.getText().toString();
            root.setOnClickListener(ignored -> edit(row));
            ViewCompat.replaceAccessibilityAction(root,
                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    context().getString(R.string.records_edit_named, name), null);
            Runnable restore = () -> adapter.notifyItemChanged(getBindingAdapterPosition());
            root.setOnLongClickListener(ignored -> {
                confirmRemove(row, restore);
                return true;
            });
            removeAction = ViewCompat.addAccessibilityAction(root, context().getString(R.string.records_remove_named, name),
                    (target, arguments) -> {
                        confirmRemove(row, restore);
                        return true;
                    });
        }
    }

    /** Swipe towards the start reveals a red remove background; the dialog confirms or restores the row. */
    private final class SwipeToRemove extends ItemTouchHelper.SimpleCallback {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;

        SwipeToRemove() {
            super(0, ItemTouchHelper.START);
            paint.setColor(context().getColor(R.color.finora_error));
            icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context(), R.drawable.finora_ic_delete);
            if (icon != null) icon.setTint(context().getColor(R.color.finora_error_ink));
        }

        @Override public int getSwipeDirs(RecyclerView recycler, RecyclerView.ViewHolder holder) {
            return holder instanceof RowHolder && !model.work.busy() ? super.getSwipeDirs(recycler, holder) : 0;
        }

        @Override public boolean onMove(RecyclerView recycler, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
            return false;
        }

        @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
            int position = holder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            confirmRemove(adapter.entry(position).row(), () -> {
                // Put the swiped view back; the change animation then rebinds the row in place.
                clearView(view.recordsList, holder);
                adapter.notifyItemChanged(position);
            });
        }

        @Override public void onChildDraw(Canvas canvas, RecyclerView recycler, RecyclerView.ViewHolder holder,
                                          float dx, float dy, int state, boolean active) {
            View item = holder.itemView;
            if (dx != 0) {
                boolean rtl = recycler.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                float left = rtl ? item.getLeft() : item.getRight() + dx;
                float right = rtl ? item.getLeft() + dx : item.getRight();
                canvas.drawRoundRect(new RectF(left, item.getTop(), right, item.getBottom()), dp(18), dp(18), paint);
                if (icon != null) {
                    int size = dp(24);
                    int top = item.getTop() + (item.getHeight() - size) / 2;
                    int start = rtl ? item.getLeft() + dp(24) : item.getRight() - dp(24) - size;
                    icon.setBounds(start, top, start + size, top + size);
                    icon.draw(canvas);
                }
            }
            super.onChildDraw(canvas, recycler, holder, dx, dy, state, active);
        }
    }
}
