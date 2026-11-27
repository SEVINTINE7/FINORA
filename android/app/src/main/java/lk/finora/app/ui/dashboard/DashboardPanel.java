package lk.finora.app.ui.dashboard;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

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
    private final LinearLayout charts;
    private final LinearLayout highlights;
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
        highlights = column();
        content.addView(highlights);
        charts = column();
        content.addView(charts);
        bindHighlights(activity);
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
        view.setTextColor(getContext().getColor(target == results || target == content
                ? R.color.finora_on_navy : R.color.finora_ink));
        view.setTextSize(heading ? 20 : 16);
        int spacing = dp(6);
        view.setPadding(0, spacing, 0, spacing);
        ViewCompat.setAccessibilityHeading(view, heading);
        target.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void render() {
        results.removeAllViews();
        charts.removeAllViews();
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
        var summary = card(results, R.string.dashboard_monthly_balance);
        total(summary, R.string.dashboard_net, value.net(), true);
        total(summary, R.string.dashboard_income, value.income(), false);
        total(summary, R.string.dashboard_expense, value.expense(), false);
        if (value.income().signum() == 0 && value.expense().signum() == 0) {
            line(results, getContext().getText(R.string.dashboard_empty), false);
        }
        var categories = card(charts, R.string.dashboard_categories);
        if (value.byCategory().isEmpty()) {
            line(categories, getContext().getText(R.string.dashboard_no_expenses), false);
        } else {
            addChart(categories, expenseChart(value));
            String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
            for (int i = 0; i < value.byCategory().size(); i++) {
                var row = value.byCategory().get(i);
                String name = row.name() == null ? getContext().getString(R.string.dashboard_uncategorized)
                        : "si".equals(language) && row.nameSi() != null && !row.nameSi().isBlank()
                                ? row.nameSi() : row.name();
                var key = new LinearLayout(getContext());
                key.setGravity(android.view.Gravity.CENTER_VERTICAL);
                var swatch = new View(getContext());
                swatch.setBackgroundColor(PALETTE[i % PALETTE.length]);
                swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                var marker = new LinearLayout.LayoutParams(dp(12), dp(12));
                marker.setMarginEnd(dp(6));
                key.addView(swatch, marker);
                line(key, getContext().getString(R.string.dashboard_category_amount, name,
                        amount(row.total())), false);
                categories.addView(key);
            }
        }
        var trend = card(charts, R.string.dashboard_trend);
        addChart(trend, trendChart(value));
        for (var row : value.trend()) {
            line(trend, monthLabel(YearMonth.parse(row.month())), true);
            total(trend, R.string.dashboard_income, row.income(), false);
            total(trend, R.string.dashboard_expense, row.expense(), false);
            total(trend, R.string.dashboard_net, row.net(), false);
        }
    }

    private void total(LinearLayout target, int label, BigDecimal value, boolean heading) {
        line(target, getContext().getString(label, amount(value)), heading);
    }

    private LinearLayout column() {
        var target = new LinearLayout(getContext());
        target.setOrientation(LinearLayout.VERTICAL);
        return target;
    }

    private LinearLayout card(LinearLayout parent, int title) {
        var card = new MaterialCardView(getContext());
        card.setCardBackgroundColor(getContext().getColor(R.color.finora_field));
        card.setStrokeColor(getContext().getColor(R.color.finora_outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setCardElevation(0);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        var body = column();
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(body);
        parent.addView(card, params);
        line(body, getContext().getText(title), true);
        return body;
    }

    private void bindHighlights(AppCompatActivity activity) {
        var data = model.highlights;
        bind(activity, data.score, R.string.score_heading, (body, score) -> {
            if (score.total() == null) {
                line(body, getContext().getText(R.string.score_unavailable), false);
                return;
            }
            var ring = new CircularProgressIndicator(getContext());
            ring.setIndicatorSize(dp(64));
            ring.setTrackThickness(dp(6));
            ring.setIndicatorColor(getContext().getColor(R.color.finora_green));
            ring.setTrackColor(getContext().getColor(R.color.finora_disabled));
            ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            body.addView(ring);
            ring.setProgress(score.total());
            line(body, getContext().getString(R.string.score_total, score.total().toString()), false);
        });
        Runnable renderBudgets = bind(activity, data.budgets, R.string.budget_list_title, (body, rows) -> {
            if (rows.isEmpty()) empty(body);
            var categories = data.categories.value;
            var locale = getResources().getConfiguration().getLocales().get(0);
            for (var budget : rows) {
                var category = categories == null ? null : categories.stream()
                        .filter(item -> item.id() == budget.categoryId()).findFirst().orElse(null);
                String name = category == null ? getContext().getString(R.string.budget_unknown_category)
                        : "si".equals(locale.getLanguage()) && category.nameSi() != null
                                && !category.nameSi().isBlank() ? category.nameSi() : category.name();
                line(body, name, true);
                boolean over = budget.remaining().signum() < 0;
                int label = over ? R.string.budget_over_by : R.string.budget_left;
                line(body, getContext().getString(label, amount(budget.used()), amount(budget.limitAmount()),
                        amount(budget.remaining().abs())), false);
                progress(body, percent(budget.used(), budget.limitAmount()), over);
            }
        });
        data.categories.work.changes.observe(activity, ignored -> renderBudgets.run());
        bind(activity, data.bills, R.string.dashboard_upcoming_bills, (body, rows) -> {
            if (rows.isEmpty()) empty(body);
            for (var bill : rows) {
                line(body, bill.name(), true);
                line(body, getContext().getString(R.string.bill_details, amount(bill.amount()),
                        bill.dueDate()), false);
            }
        });
        bind(activity, data.recent, R.string.dashboard_recent_transactions, (body, rows) -> {
            if (rows.isEmpty()) empty(body);
            for (var row : rows) {
                if (row.merchant() != null && !row.merchant().isBlank()) line(body, row.merchant(), true);
                int label = "INCOME".equals(row.type()) ? R.string.dashboard_income : R.string.dashboard_expense;
                total(body, label, row.amount(), false);
                line(body, row.txnDate(), false);
            }
        });
        bind(activity, data.goals, R.string.dashboard_focus_goal, (body, rows) -> {
            var goal = DashboardHighlights.focus(rows);
            if (goal == null) {
                empty(body);
                return;
            }
            line(body, goal.name(), true);
            line(body, getContext().getString(R.string.goal_progress_text, amount(goal.saved()),
                    amount(goal.targetAmount()), amount(goal.remaining()), goal.progressPercent()), false);
            progress(body, goal.progressPercent(), false);
        });
    }

    private <T> Runnable bind(AppCompatActivity activity, DashboardHighlights.Section<T> section,
                              int title, java.util.function.BiConsumer<LinearLayout, T> renderValue) {
        var frame = column();
        highlights.addView(frame);
        Runnable renderSection = () -> {
            frame.removeAllViews();
            if (!section.work.isCurrent()) return;
            var body = card(frame, title);
            if (section.work.busy() || section.value == null && section.work.error == 0) {
                line(body, getContext().getText(R.string.records_loading), false);
            } else if (section.work.error != 0) {
                line(body, getContext().getText(section.work.error), false);
                body.addView(button(R.string.records_retry, model::refresh));
            } else {
                renderValue.accept(body, section.value);
            }
        };
        section.work.changes.observe(activity, ignored -> renderSection.run());
        return renderSection;
    }

    private void empty(LinearLayout body) {
        line(body, getContext().getText(R.string.records_empty), false);
    }

    private int percent(BigDecimal current, BigDecimal maximum) {
        if (maximum.signum() <= 0) return 0;
        return current.multiply(BigDecimal.valueOf(100)).divide(maximum, 0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValue();
    }

    private void progress(LinearLayout body, int percent, boolean overLimit) {
        var bar = new LinearProgressIndicator(getContext());
        bar.setIndicatorColor(getContext().getColor(overLimit ? R.color.finora_error : R.color.finora_green));
        bar.setTrackColor(getContext().getColor(R.color.finora_disabled));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        bar.setProgress(percent);
    }

    private String monthLabel(YearMonth value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy",
                getResources().getConfiguration().getLocales().get(0)));
    }

    private String amount(BigDecimal value) {
        return value.setScale(Math.max(2, value.scale()), RoundingMode.UNNECESSARY).toPlainString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addChart(LinearLayout target, Chart<?> chart) {
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        // Exact, labeled values immediately after the graphic are the accessible equivalent.
        chart.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        target.addView(chart, new LinearLayout.LayoutParams(-1, dp(160)));
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
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(54f);
        chart.setTransparentCircleRadius(54f);
        chart.setHoleColor(getContext().getColor(R.color.finora_field));
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
            colors.add(getContext().getColor(item.net().signum() < 0 ? R.color.finora_error : R.color.finora_green));
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
        axis.setZeroLineColor(getContext().getColor(R.color.finora_ink));
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
