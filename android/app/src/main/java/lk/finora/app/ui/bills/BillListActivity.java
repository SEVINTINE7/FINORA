package lk.finora.app.ui.bills;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.BillDto;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;

/** Monthly bills with a paid toggle and the due date in the shown month (T18.4). */
public final class BillListActivity extends AppCompatActivity {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private BillListViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BillListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_bill_list);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.bill_heading), true);
        findViewById(R.id.records_add).setOnClickListener(view -> open(0));
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        findViewById(R.id.bill_previous).setOnClickListener(view -> model.shiftMonth(-1));
        findViewById(R.id.bill_next).setOnClickListener(view -> model.shiftMonth(1));
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
        ((TextView) findViewById(R.id.bill_month)).setText(BudgetListActivity.monthLabel(model.month));
        findViewById(R.id.bill_previous).setEnabled(!busy);
        findViewById(R.id.bill_next).setEnabled(!busy);
        TextView summary = findViewById(R.id.bill_summary);
        summary.setText(model.summary == null ? "" : getString(R.string.bill_summary,
                plain(model.summary.total()), plain(model.summary.paid()), plain(model.summary.unpaid())));
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        LocalDate today = LocalDate.now(COLOMBO);
        for (BillDto item : model.rows) rows.addView(row(rows, item, today, busy));
    }

    private View row(LinearLayout parent, BillDto item, LocalDate today, boolean busy) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_bill, parent, false);
        ((TextView) row.findViewById(R.id.record_name)).setText(item.name());
        String due = LocalDate.parse(item.dueDate())
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
        ((TextView) row.findViewById(R.id.record_details))
                .setText(getString(R.string.bill_details, plain(item.amount()), due));

        BillListViewModel.Status status = BillListViewModel.status(item.dueDate(), item.paid(), today);
        TextView label = row.findViewById(R.id.bill_status);
        label.setText(BillListViewModel.statusLabel(status));
        label.setTextColor(getColor(BillListViewModel.statusColor(status)));

        CheckBox paid = row.findViewById(R.id.bill_paid);
        paid.setChecked(item.paid());
        paid.setEnabled(!busy);
        paid.setContentDescription(getString(R.string.bill_paid_named, item.name()));
        // The list reloads after the change, so the box always shows what the server saved
        paid.setOnCheckedChangeListener((box, checked) -> {
            if (checked != item.paid()) model.togglePaid(item);
        });

        View edit = row.findViewById(R.id.record_edit);
        View remove = row.findViewById(R.id.record_remove);
        edit.setContentDescription(getString(R.string.records_edit_named, item.name()));
        remove.setContentDescription(getString(R.string.records_remove_named, item.name()));
        edit.setOnClickListener(view -> open(item.id()));
        remove.setOnClickListener(view -> AccountListActivity.confirm(this, model.work,
                R.string.bill_remove_message, () -> model.delete(item.id())));
        return row;
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
