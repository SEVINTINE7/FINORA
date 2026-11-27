package lk.finora.app.ui.bills;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.BillDto;
import lk.finora.app.databinding.ActivityBillListBinding;
import lk.finora.app.databinding.ItemBillBinding;
import lk.finora.app.databinding.ItemBillPaidBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;

/** T63.3/D65 monthly bills: still-to-pay summary, "To pay" with Mark paid, and "Paid this month" (T18.4). */
public final class BillListActivity extends SecureActivity {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private BillListViewModel model;
    private ActivityBillListBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BillListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityBillListBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.billHeading, true);
        ViewCompat.setAccessibilityHeading(view.billDueHeader, true);
        ViewCompat.setAccessibilityHeading(view.billPaidHeader, true);
        view.recordsAdd.setOnClickListener(ignored -> open(0));
        view.recordsRetry.setOnClickListener(ignored -> model.load());
        view.billPrevious.setOnClickListener(ignored -> model.shiftMonth(-1));
        view.billNext.setOnClickListener(ignored -> model.shiftMonth(1));
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        boolean busy = model.work.busy();
        view.recordsRetry.setVisibility(!busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        view.billMonth.setText(BudgetListActivity.monthLabel(model.month));
        view.billPrevious.setEnabled(!busy);
        view.billNext.setEnabled(!busy);
        renderSummary();
        LocalDate today = LocalDate.now(COLOMBO);
        List<BillDto> due = BillListViewModel.toPay(model.rows);
        List<BillDto> paid = BillListViewModel.paidOnes(model.rows);
        section(view.billDueHeader, view.billDueCard, view.billDueRows, due.size());
        section(view.billPaidHeader, view.billPaidCard, view.billPaidRows, paid.size());
        for (int i = 0; i < due.size(); i++) {
            if (i > 0) divider(view.billDueRows);
            view.billDueRows.addView(dueRow(due.get(i), today, busy));
        }
        for (int i = 0; i < paid.size(); i++) {
            if (i > 0) divider(view.billPaidRows);
            view.billPaidRows.addView(paidRow(paid.get(i), busy));
        }
    }

    private void renderSummary() {
        var summary = model.summary;
        view.billSummary.setVisibility(summary == null || model.rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (summary == null) return;
        int paidCount = BillListViewModel.paidOnes(model.rows).size();
        view.billSummaryAmount.setText(Money.rupees(this, summary.unpaid()));
        view.billSummaryTotal.setText(getString(R.string.bill_of_total, Money.rupees(this, summary.total())));
        view.billSummaryCount.setText(getString(R.string.bill_paid_count, paidCount, model.rows.size(),
                Money.rupees(this, summary.paid())));
        view.billSummaryBar.setTrackStopIndicatorSize(0);
        view.billSummaryBar.setIndicatorTrackGapSize(0);
        view.billSummaryBar.setProgressCompat(BillListViewModel.percentPaid(summary), false);
        // TalkBack keeps the exact LKR sentence used since T18.4.
        view.billSummary.setContentDescription(getString(R.string.bill_summary,
                plain(summary.total()), plain(summary.paid()), plain(summary.unpaid())));
    }

    private static void section(View header, View card, LinearLayout rows, int count) {
        rows.removeAllViews();
        header.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        card.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private void divider(LinearLayout rows) {
        var line = new View(this);
        line.setBackgroundColor(getColor(R.color.finora_card_line));
        var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
        params.setMarginStart(Math.round(getResources().getDisplayMetrics().density * 14));
        rows.addView(line, params);
    }

    private View dueRow(BillDto item, LocalDate today, boolean busy) {
        var row = ItemBillBinding.inflate(LayoutInflater.from(this), view.billDueRows, false);
        LocalDate due = LocalDate.parse(item.dueDate());
        var status = BillListViewModel.status(item.dueDate(), false, today);
        boolean late = status == BillListViewModel.Status.OVERDUE;
        boolean dueToday = due.equals(today);
        int ink = getColor(late ? R.color.finora_error : dueToday ? R.color.finora_warn_ink : R.color.finora_muted);
        int tile = getColor(late ? R.color.finora_error_surface : dueToday ? R.color.finora_warn_fill
                : R.color.finora_card_line);

        row.billTileDay.setText(String.valueOf(due.getDayOfMonth()));
        row.billTileMonth.setText(due.format(DateTimeFormatter.ofPattern("MMM", locale())).toUpperCase(locale()));
        row.billTile.setBackgroundTintList(ColorStateList.valueOf(tile));
        int tileInk = late || dueToday ? ink : getColor(R.color.finora_ink);
        row.billTileDay.setTextColor(tileInk);
        row.billTileMonth.setTextColor(tileInk);

        row.recordName.setText(item.name());
        row.recordDetails.setText(Money.rupees(this, item.amount()));
        String when = late
                ? getResources().getQuantityString(R.plurals.bill_overdue_days, BillListViewModel.daysLate(item.dueDate(), today),
                        BillListViewModel.daysLate(item.dueDate(), today))
                : dueToday ? getString(R.string.bill_due_today)
                : getString(R.string.bill_due_on, due.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale())));
        row.billStatus.setText(when);
        row.billStatus.setTextColor(ink);
        row.billStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(late ? R.drawable.finora_ic_alert
                : R.drawable.finora_ic_calendar, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(row.billStatus, ColorStateList.valueOf(ink));
        scaleStatusIcon(row.billStatus);

        bindRow(row.billRow, item, getString(BillListViewModel.statusLabel(status)) + ", " + when);
        bindPaid(row.billPaid, item, busy);
        return row.getRoot();
    }

    private View paidRow(BillDto item, boolean busy) {
        var row = ItemBillPaidBinding.inflate(LayoutInflater.from(this), view.billPaidRows, false);
        LocalDate due = LocalDate.parse(item.dueDate());
        row.billTileDay.setText(due.format(DateTimeFormatter.ofPattern("d MMM", locale())));
        row.recordName.setText(item.name());
        row.recordDetails.setText(Money.rupees(this, item.amount()));
        bindRow(row.billRow, item, getString(R.string.bill_paid));
        bindPaid(row.billPaid, item, busy);
        return row.getRoot();
    }

    /** Tap edits; long-press or the TalkBack action removes after the existing confirmation. */
    private void bindRow(View row, BillDto item, String state) {
        String due = LocalDate.parse(item.dueDate())
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
        row.setContentDescription(item.name() + ". " + getString(R.string.bill_details, plain(item.amount()), due));
        ViewCompat.setStateDescription(row, state);
        row.setOnClickListener(ignored -> open(item.id()));
        ViewCompat.replaceAccessibilityAction(row,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.records_edit_named, item.name()), null);
        Runnable remove = () -> AccountListActivity.confirm(this, model.work,
                R.string.bill_remove_message, () -> model.delete(item.id()));
        row.setOnLongClickListener(ignored -> {
            remove.run();
            return true;
        });
        ViewCompat.addAccessibilityAction(row, getString(R.string.records_remove_named, item.name()),
                (target, arguments) -> {
                    remove.run();
                    return true;
                });
    }

    /** The list reloads after the change, so the toggle always shows what the server saved. */
    private void bindPaid(com.google.android.material.button.MaterialButton button, BillDto item, boolean busy) {
        button.setChecked(item.paid());
        button.setEnabled(!busy);
        button.setContentDescription(getString(R.string.bill_paid_named, item.name()));
        button.addOnCheckedChangeListener((ignored, checked) -> {
            if (checked != item.paid()) model.togglePaid(item);
        });
    }

    private void scaleStatusIcon(android.widget.TextView text) {
        var icon = text.getCompoundDrawablesRelative()[0];
        if (icon == null) return;
        int size = Math.round(text.getTextSize());
        icon.setBounds(0, 0, size, size);
        text.setCompoundDrawablesRelative(icon, null, null, null);
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private void open(long id) {
        if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
            startActivity(new Intent(this, BillFormActivity.class).putExtra(AccountListActivity.ITEM_ID, id));
        }
    }

    private static String plain(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
