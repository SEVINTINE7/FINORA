package lk.finora.app.ui.dashboard;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;

import lk.finora.app.R;
import lk.finora.app.data.repo.DashboardRepository;

public final class DashboardPanel {
    private static final int[] PALETTE = {0xff5ee7af, 0xff72aafa, 0xffffb465, 0xffe793d9, 0xffdae878, 0xffa78bfa};
    private final DashboardViewModel model;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final LinearLayout results;
    private final TextView month;
    private final Button previous;
    private final Button next;

    public DashboardPanel(AppCompatActivity activity, DashboardViewModel model) {
        this.model = model;
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        var navigation = new LinearLayout(activity);
        previous = button(R.string.dashboard_previous, () -> model.step(-1));
        next = button(R.string.dashboard_next, () -> model.step(1));
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(navigation);
        month = line(content, "", true);
        results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        model.work.changes.observe(activity, ignored -> {
            model.refreshIfQueued();
            render();
        });
    }

    public ScrollView view() { return scroll; }

    private android.content.Context getContext() { return scroll.getContext(); }

    private android.content.res.Resources getResources() { return scroll.getResources(); }

    private Button button(int label, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView line(LinearLayout target, CharSequence text, boolean heading) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextColor(getContext().getColor(R.color.finora_on_navy));
        view.setTextSize(heading ? 20 : 16);
        int spacing = dp(6);
        view.setPadding(0, spacing, 0, spacing);
        ViewCompat.setAccessibilityHeading(view, heading);
        target.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void render() {
        results.removeAllViews();
        if (!model.work.isCurrent()) return;
        month.setText(monthLabel(model.month));
        previous.setEnabled(!model.work.busy() && model.canStep(-1));
        next.setEnabled(!model.work.busy() && model.canStep(1));
        if (model.work.busy()) {
            line(results, getContext().getText(R.string.records_loading), false);
        } else if (model.work.error != 0) {
            line(results, getContext().getText(model.work.error), false);
            results.addView(button(R.string.records_retry, model::refresh));
        } else if (model.snapshot != null) {
            showSnapshot(model.snapshot);
        }
    }

    private void showSnapshot(DashboardRepository.Snapshot value) {
        line(results, getContext().getString(R.string.dashboard_income, amount(value.income())), false);
        line(results, getContext().getString(R.string.dashboard_expense, amount(value.expense())), false);
        line(results, getContext().getString(R.string.dashboard_net, amount(value.net())), false);
        if (value.income().signum() == 0 && value.expense().signum() == 0) {
            line(results, getContext().getText(R.string.dashboard_empty), false);
        }
        line(results, getContext().getText(R.string.dashboard_categories), true);
        if (value.byCategory().isEmpty()) {
            line(results, getContext().getText(R.string.dashboard_no_expenses), false);
        } else {
            addChart(expenseChart(value));
            String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
            for (int i = 0; i < value.byCategory().size(); i++) {
                var row = value.byCategory().get(i);
                String name = row.name() == null ? getContext().getString(R.string.dashboard_uncategorized)
                        : "si".equals(language) && row.nameSi() != null && !row.nameSi().isBlank()
                                ? row.nameSi() : row.name();
                line(results, getContext().getString(R.string.dashboard_category_amount, name, amount(row.total())),
                        false).setTextColor(PALETTE[i % PALETTE.length]);
            }
        }
        line(results, getContext().getText(R.string.dashboard_trend), true);
        addChart(trendChart(value));
        for (var row : value.trend()) {
            line(results, monthLabel(YearMonth.parse(row.month())), true);
            line(results, getContext().getString(R.string.dashboard_income, amount(row.income())), false);
            line(results, getContext().getString(R.string.dashboard_expense, amount(row.expense())), false);
            line(results, getContext().getString(R.string.dashboard_net, amount(row.net())), false);
        }
    }

    private String monthLabel(YearMonth value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy",
                getResources().getConfiguration().getLocales().get(0)));
    }

    private String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addChart(Chart<?> chart) {
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        // Exact, labeled values immediately after the graphic are the accessible equivalent.
        chart.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        results.addView(chart, new LinearLayout.LayoutParams(-1, dp(160)));
    }

    private PieChart expenseChart(DashboardRepository.Snapshot snapshot) {
        var entries = new ArrayList<PieEntry>();
        for (var category : snapshot.byCategory()) {
            entries.add(new PieEntry(chartRatio(category.total(), snapshot.expense())));
        }
        var slices = new PieDataSet(entries, "");
        slices.setColors(PALETTE);
        slices.setDrawValues(false);
        var chart = new PieChart(getContext());
        chart.setDrawHoleEnabled(false);
        chart.setDrawEntryLabels(false);
        chart.setData(new PieData(slices));
        return chart;
    }

    private BarChart trendChart(DashboardRepository.Snapshot snapshot) {
        BigDecimal max = BigDecimal.ONE;
        for (var item : snapshot.trend()) max = max.max(item.net().abs());
        var entries = new ArrayList<BarEntry>();
        var colors = new ArrayList<Integer>();
        for (var item : snapshot.trend()) {
            entries.add(new BarEntry(entries.size(), chartRatio(item.net(), max)));
            colors.add(item.net().signum() < 0 ? PALETTE[2] : PALETTE[0]);
        }
        var bars = new BarDataSet(entries, "");
        bars.setColors(colors);
        bars.setDrawValues(false);
        var chart = new BarChart(getContext());
        chart.getXAxis().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        var axis = chart.getAxisLeft();
        axis.setDrawLabels(false);
        axis.setDrawGridLines(false);
        axis.setDrawAxisLine(false);
        axis.setDrawZeroLine(true);
        axis.setZeroLineColor(getContext().getColor(R.color.finora_on_navy));
        axis.setAxisMinimum(-1.1f);
        axis.setAxisMaximum(1.1f);
        chart.setFitBars(true);
        chart.setData(new BarData(bars));
        return chart;
    }

    private float chartRatio(BigDecimal value, BigDecimal max) {
        return value.divide(max, MathContext.DECIMAL32).floatValue();
    }
}
