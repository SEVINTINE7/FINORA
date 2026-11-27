package lk.finora.app.ui.history;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityFinoraScreenBinding;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.MonthlyRepository;
import lk.finora.app.data.repo.MonthlyRepository.Month;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.budgets.BudgetListActivity;

public final class HistoryActivity extends SecureActivity {
    private Model model;
    private LinearLayout page;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(Model.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        var binding = ActivityFinoraScreenBinding.inflate(getLayoutInflater());
        page = binding.screenPage;
        setContentView(binding.getRoot());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        page.removeAllViews();
        if (!AccountListActivity.verify(this, model.work)) return;
        line(getString(R.string.history_title), true);
        line(getString(R.string.insight_period, BudgetListActivity.monthLabel(model.end.minusMonths(model.count - 1L)),
                BudgetListActivity.monthLabel(model.end)), false);
        button(R.string.dashboard_previous, () -> model.shift(-1));
        button(R.string.dashboard_next, () -> model.shift(1));
        for (int count : new int[]{6, 12, 24}) {
            Button range = ScreenControls.action(this);
            range.setText(getString(R.string.history_range, Integer.toString(count)));
            range.setEnabled(!model.work.busy() && model.count != count);
            range.setOnClickListener(view -> model.range(count));
            page.addView(range);
        }
        if (model.work.busy()) {
            line(getString(R.string.records_loading), false);
        } else if (model.work.error != 0) {
            line(getString(model.work.error), false);
            button(R.string.records_retry, model::load);
        } else if (model.rows != null && model.rows.length == 0) {
            line(getString(R.string.history_empty), false);
        } else if (model.rows != null) {
            if (java.util.Arrays.stream(model.rows).anyMatch(Month::refreshPending)) {
                button(R.string.records_retry, model::load);
            }
            showTrend();
            for (Month row : model.rows) {
                line(BudgetListActivity.monthLabel(YearMonth.parse(row.month())), true);
                if (row.reconstructedAt() != null) {
                    line(getString(R.string.history_reconstructed), false);
                }
                if (row.refreshPending()) {
                    line(getString(R.string.history_refresh_pending), false);
                    continue;
                }
                line(getString(R.string.dashboard_income, row.income().toPlainString()), false);
                line(getString(R.string.dashboard_expense, row.expense().toPlainString()), false);
                line(getString(R.string.dashboard_net, row.net().toPlainString()), false);
                line(row.score() == null ? getString(R.string.score_unavailable)
                        : getString(R.string.score_total, row.score().toString()), false);
                for (var pattern : row.insights()) {
                    int label = switch (pattern.type()) {
                        case "HIGH_WANTS" -> R.string.insight_wants;
                        case "HIGH_FIXED_BILLS" -> R.string.insight_bills;
                        case "PAYDAY_SPENDING" -> R.string.insight_payday;
                        case "WEEKEND_SPENDING" -> R.string.insight_weekend;
                        default -> 0;
                    };
                    if (label != 0) line(getString(label), false);
                }
            }
        }
    }

    private void showTrend() {
        var data = new LineData();
        var points = new ArrayList<Entry>();
        YearMonth last = null;
        for (Month row : model.rows) {
            YearMonth current = YearMonth.parse(row.month());
            if (row.score() == null || (last != null && !current.equals(last.plusMonths(1)))) {
                addSegment(data, points);
                points = new ArrayList<>();
            }
            if (row.score() != null) {
                points.add(new Entry(java.time.temporal.ChronoUnit.MONTHS.between(model.end.minusMonths(model.count - 1L),
                        current), row.score()));
            }
            last = current;
        }
        addSegment(data, points);
        if (data.getDataSetCount() == 0) return;
        line(getString(R.string.history_chart), true);
        var chart = new LineChart(this);
        chart.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getXAxis().setAxisMinimum(0);
        chart.getXAxis().setAxisMaximum(model.count - 1);
        chart.getXAxis().setDrawLabels(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setAxisMaximum(100);
        chart.setData(data);
        page.addView(chart, new LinearLayout.LayoutParams(-1,
                Math.round(156 * getResources().getDisplayMetrics().density)));
    }

    private void addSegment(LineData data, ArrayList<Entry> points) {
        if (points.isEmpty()) return;
        var set = new LineDataSet(points, "");
        set.setColor(getColor(R.color.finora_ink));
        set.setCircleColor(getColor(R.color.finora_ink));
        set.setDrawValues(false);
        data.addDataSet(set);
    }

    private void button(int title, Runnable action) {
        var control = ScreenControls.action(this);
        control.setText(title);
        control.setEnabled(!model.work.busy());
        control.setOnClickListener(view -> action.run());
        page.addView(control);
    }

    private void line(String value, boolean heading) {
        var label = ScreenControls.text(this);
        label.setText(value);
        label.setTextColor(getColor(R.color.finora_ink));
        label.setTextSize(heading ? 20 : 16);
        ViewCompat.setAccessibilityHeading(label, heading);
        page.addView(label);
    }

    public static final class Model extends ViewModel {
        final Work work = new Work(() -> rows = null);
        private final MonthlyRepository repository = work.requests == null ? null : new MonthlyRepository(work.requests);
        YearMonth end = YearMonth.now(ZoneId.of("Asia/Colombo")).minusMonths(1);
        int count = 6;
        Month[] rows;

        void load() {
            if (!work.isCurrent() || work.busy()) return;
            rows = null;
            YearMonth to = end;
            YearMonth from = to.minusMonths(count - 1L);
            work.run(() -> repository.history(from, to), response -> {
                Month[] result = AccountRepository.read(response, Month[].class);
                YearMonth previous = from.minusMonths(1);
                for (Month row : result) {
                    if (!validMonth(row)) {
                        throw new IOException("Invalid history data");
                    }
                    YearMonth month = YearMonth.parse(row.month());
                    if (!month.isAfter(previous) || month.isAfter(to)) throw new IOException("Invalid history range");
                    previous = month;
                }
                return result;
            }, value -> rows = value, false);
        }

        private static boolean validMonth(Month row) {
            if (row == null || row.insights() == null) return false;
            if (row.refreshPending()) {
                return row.income() == null && row.expense() == null && row.net() == null
                        && row.score() == null && row.insights().isEmpty();
            }
            return row.income() != null && row.expense() != null && row.net() != null
                    && row.income().signum() >= 0 && row.expense().signum() >= 0
                    && row.income().subtract(row.expense()).compareTo(row.net()) == 0
                    && row.insights().stream().noneMatch(pattern -> pattern == null || pattern.type() == null)
                    && (row.score() == null || (row.score() >= 0 && row.score() <= 100));
        }

        void shift(int by) {
            YearMonth candidate = end.plusMonths(by);
            if (candidate.minusMonths(count - 1L).getYear() < 1000
                    || candidate.isAfter(YearMonth.now(ZoneId.of("Asia/Colombo")).minusMonths(1))) return;
            end = candidate;
            load();
        }

        void range(int months) {
            if (end.minusMonths(months - 1L).getYear() < 1000) return;
            count = months;
            load();
        }

        @Override protected void onCleared() { work.close(); }
    }
}
