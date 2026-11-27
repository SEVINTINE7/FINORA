package lk.finora.app.ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import lk.finora.app.R;
import lk.finora.app.data.api.BillDto;
import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.repo.DashboardRepository;
import lk.finora.app.databinding.ItemTransactionRowBinding;
import lk.finora.app.databinding.PanelDashboardBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.advice.AdviceActivity;
import lk.finora.app.ui.bills.BillListActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;
import lk.finora.app.ui.transactions.TransactionRows;

/** T62.3/D65 Home: greeting, month switcher, balance, score, recent records, budgets, bills, goal and charts. */
public final class DashboardPanel {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final int[] PALETTE = {R.color.finora_green, R.color.finora_chart_2, R.color.finora_chart_3,
            R.color.finora_chart_4, R.color.finora_chart_5, R.color.finora_chart_6, R.color.finora_chart_7,
            R.color.finora_chart_8};
    private final AppCompatActivity activity;
    private final DashboardViewModel model;
    private final PanelDashboardBinding view;
    private Runnable openRecords = () -> {};

    public DashboardPanel(AppCompatActivity activity, DashboardViewModel model) {
        this.activity = activity;
        this.model = model;
        view = PanelDashboardBinding.inflate(LayoutInflater.from(activity));
        markHeadings(view.getRoot());
        view.dashboardPrevious.setOnClickListener(ignored -> model.step(-1));
        view.dashboardNext.setOnClickListener(ignored -> model.step(1));
        view.dashboardRetry.setOnClickListener(ignored -> model.refresh());
        view.dashboardRecentAll.setOnClickListener(ignored -> openRecords.run());
        view.dashboardAdvice.setOnClickListener(ignored -> open(AdviceActivity.class));
        view.dashboardBudgetsManage.setOnClickListener(ignored -> open(BudgetListActivity.class));
        view.dashboardBillsAll.setOnClickListener(ignored -> open(BillListActivity.class));
        setUpCharts();
        showGreeting();
        model.work.changes.observe(activity, ignored -> {
            model.refreshIfQueued();
            render();
        });
        bindHighlights();
    }

    public View view() { return view.getRoot(); }

    /** "See all" on recent transactions switches the shell to the Records tab. */
    public void onOpenRecords(Runnable action) { openRecords = action; }

    private void open(Class<?> screen) { activity.startActivity(new Intent(activity, screen)); }

    private Context context() { return activity; }

    private void showGreeting() {
        int hour = LocalTime.now(COLOMBO).getHour();
        view.dashboardGreeting.setText(hour < 12 ? R.string.dashboard_greeting_morning
                : hour < 17 ? R.string.dashboard_greeting_afternoon : R.string.dashboard_greeting_evening);
    }

    private void render() {
        if (!model.work.isCurrent()) return;
        showGreeting();
        view.dashboardMonth.setText(monthLabel(model.month, "MMM yyyy"));
        view.dashboardMonth.setContentDescription(monthLabel(model.month, "LLLL yyyy"));
        boolean busy = model.work.busy();
        view.dashboardPrevious.setEnabled(!busy && model.canStep(-1));
        view.dashboardNext.setEnabled(!busy && model.canStep(1));
        view.dashboardPrevious.setAlpha(view.dashboardPrevious.isEnabled() ? 1f : 0.38f);
        view.dashboardNext.setAlpha(view.dashboardNext.isEnabled() ? 1f : 0.38f);
        boolean failed = !busy && model.work.error != 0;
        status(view.dashboardStatus, busy ? R.string.records_loading : failed ? model.work.error : 0, failed);
        view.dashboardRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        var snapshot = busy || failed ? null : model.snapshot;
        view.dashboardBalanceCard.setVisibility(snapshot == null ? View.GONE : View.VISIBLE);
        view.dashboardEmpty.setVisibility(View.GONE);
        if (snapshot != null) showSnapshot(snapshot);
        else clearCharts();
    }

    private void showSnapshot(DashboardRepository.Snapshot value) {
        view.dashboardNetAmount.setText(Money.rupees(context(), value.net()));
        view.dashboardNetAmount.setContentDescription(context().getString(R.string.dashboard_net, Money.amount(value.net())));
        view.dashboardIncomeAmount.setText(Money.rupees(context(), value.income()));
        view.dashboardIncomeTile.setContentDescription(context().getString(R.string.dashboard_income,
                Money.amount(value.income())));
        view.dashboardExpenseAmount.setText(Money.rupees(context(), value.expense()));
        view.dashboardExpenseTile.setContentDescription(context().getString(R.string.dashboard_expense,
                Money.amount(value.expense())));
        view.dashboardEmpty.setVisibility(value.income().signum() == 0 && value.expense().signum() == 0
                ? View.VISIBLE : View.GONE);
        showCategories(value);
        showTrend(value);
    }

    private void showCategories(DashboardRepository.Snapshot value) {
        var legend = view.dashboardCategoryLegend;
        legend.removeAllViews();
        PieChart chart = view.dashboardCategoryChart;
        if (value.byCategory().isEmpty()) {
            chart.setVisibility(View.GONE);
            legend.addView(caption(context().getText(R.string.dashboard_no_expenses)));
            return;
        }
        chart.setVisibility(View.VISIBLE);
        var entries = new ArrayList<PieEntry>();
        var colors = new ArrayList<Integer>();
        for (int i = 0; i < value.byCategory().size(); i++) {
            var row = value.byCategory().get(i);
            entries.add(new PieEntry(ratio(row.total(), value.expense())));
            int color = context().getColor(PALETTE[i % PALETTE.length]);
            colors.add(color);
            String name = row.name() == null ? context().getString(R.string.dashboard_uncategorized)
                    : sinhala() && row.nameSi() != null && !row.nameSi().isBlank() ? row.nameSi() : row.name();
            legend.addView(legendRow(color, name, Money.amount(row.total()),
                    context().getString(R.string.dashboard_category_amount, name, Money.amount(row.total()))));
        }
        var slices = new PieDataSet(entries, "");
        slices.setColors(colors);
        slices.setDrawValues(false);
        slices.setSliceSpace(2f);
        chart.setCenterText(context().getString(R.string.dashboard_spent, Money.compact(value.expense())));
        chart.setData(new PieData(slices));
        chart.invalidate();
    }

    private void showTrend(DashboardRepository.Snapshot value) {
        BigDecimal max = BigDecimal.ONE;
        boolean negative = false;
        for (var item : value.trend()) {
            max = max.max(item.net().abs());
            negative |= item.net().signum() < 0;
        }
        // Only reserve room below the zero line when a month actually lost money.
        view.dashboardTrendChart.getAxisLeft().setAxisMinimum(negative ? -1.1f : 0f);
        var entries = new ArrayList<BarEntry>();
        var colors = new ArrayList<Integer>();
        var labels = view.dashboardTrendLabels;
        labels.removeAllViews();
        for (int i = 0; i < value.trend().size(); i++) {
            var item = value.trend().get(i);
            boolean last = i == value.trend().size() - 1;
            entries.add(new BarEntry(i, ratio(item.net(), max)));
            colors.add(context().getColor(item.net().signum() < 0 ? R.color.finora_error
                    : last ? R.color.finora_green : R.color.finora_chart_4));
            YearMonth month = YearMonth.parse(item.month());
            var column = new LinearLayout(context());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setFocusable(true);
            column.setContentDescription(monthLabel(month, "LLLL yyyy") + ", "
                    + context().getString(R.string.dashboard_net, Money.amount(item.net())));
            TextView name = caption(monthLabel(month, "LLL"));
            name.setGravity(Gravity.CENTER);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView net = caption(Money.compact(item.net()));
            net.setGravity(Gravity.CENTER);
            net.setTextColor(context().getColor(item.net().signum() < 0 ? R.color.finora_error : R.color.finora_ink));
            net.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            column.addView(name);
            column.addView(net);
            labels.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        }
        var bars = new BarDataSet(entries, "");
        bars.setColors(colors);
        bars.setDrawValues(false);
        var data = new BarData(bars);
        data.setBarWidth(0.55f);
        view.dashboardTrendChart.setData(data);
        view.dashboardTrendChart.invalidate();
    }

    private void clearCharts() {
        view.dashboardCategoryChart.clear();
        view.dashboardTrendChart.clear();
        view.dashboardCategoryLegend.removeAllViews();
        view.dashboardTrendLabels.removeAllViews();
    }

    private void bindHighlights() {
        var data = model.highlights;
        data.name.work.changes.observe(activity, ignored -> {
            String name = data.name.value;
            view.dashboardName.setText(name == null || name.isEmpty() ? context().getString(R.string.home_tab) : name);
        });
        data.score.work.changes.observe(activity, ignored -> renderScore());
        Runnable recent = bind(data.recent, view.dashboardRecent, this::renderRecent);
        Runnable budgets = bind(data.budgets, view.dashboardBudgets, this::renderBudgets);
        data.categories.work.changes.observe(activity, ignored -> {
            budgets.run();
            recent.run();
        });
        bind(data.bills, view.dashboardBills, this::renderBills);
        bind(data.goals, view.dashboardGoal, this::renderGoal);
    }

    private void renderScore() {
        var section = model.highlights.score;
        var ring = view.dashboardScoreRing;
        if (!section.work.isCurrent()) return;
        if (section.work.busy() || section.value == null && section.work.error == 0) {
            ring.setProgressCompat(0, false);
            view.dashboardScoreValue.setText("");
            view.dashboardScoreText.setText(R.string.records_loading);
            return;
        }
        if (section.work.error != 0 || section.value.total() == null) {
            ring.setProgress(0);
            view.dashboardScoreValue.setText("–");
            view.dashboardScoreText.setText(section.work.error != 0 ? section.work.error : R.string.score_unavailable);
            return;
        }
        int total = section.value.total();
        ring.setProgressCompat(total, false);
        view.dashboardScoreValue.setText(String.valueOf(total));
        view.dashboardScoreText.setText(context().getString(R.string.score_total, String.valueOf(total)));
    }

    private void renderBudgets(LinearLayout body, List<BudgetDto> rows) {
        if (rows.isEmpty()) {
            body.addView(padded(caption(context().getText(R.string.records_empty))));
            return;
        }
        var categories = model.highlights.categories.value;
        for (var budget : rows) {
            var category = TransactionRows.category(categories, budget.categoryId());
            String name = category == null ? context().getString(R.string.budget_unknown_category)
                    : TransactionRows.displayName(context(), category);
            boolean over = budget.remaining().signum() < 0;
            int percent = percent(budget.used(), budget.limitAmount());
            var item = new LinearLayout(context());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(16), 0, 0);
            item.setFocusable(true);
            item.setContentDescription(name + ", " + context().getString(over ? R.string.budget_over_by : R.string.budget_left,
                    Money.amount(budget.used()), Money.amount(budget.limitAmount()), Money.amount(budget.remaining().abs())));
            var top = new LinearLayout(context());
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = rowTitle(name);
            top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView state = rowTitle(context().getString(over ? R.string.budget_over_short : R.string.budget_left_short,
                    Money.rupees(context(), budget.remaining().abs())));
            state.setTextSize(13);
            state.setTextColor(context().getColor(over ? R.color.finora_error : R.color.finora_green));
            top.addView(state);
            item.addView(top);
            var bar = new LinearProgressIndicator(context());
            bar.setTrackThickness(dp(8));
            bar.setTrackCornerRadius(dp(4));
            bar.setIndicatorColor(context().getColor(over ? R.color.finora_error
                    : percent >= 90 ? R.color.finora_chart_3 : R.color.finora_green));
            bar.setTrackColor(context().getColor(R.color.finora_disabled));
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setTrackStopIndicatorSize(0);
        bar.setIndicatorTrackGapSize(0);
            bar.setProgressCompat(percent, false);
            var barParams = new LinearLayout.LayoutParams(-1, -2);
            barParams.topMargin = dp(8);
            barParams.bottomMargin = dp(6);
            item.addView(bar, barParams);
            item.addView(caption(context().getString(R.string.budget_used_of,
                    Money.rupees(context(), budget.used()), Money.rupees(context(), budget.limitAmount()))));
            for (int i = 0; i < item.getChildCount(); i++) {
                item.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            body.addView(item);
        }
    }

    private void renderBills(LinearLayout body, List<BillDto> rows) {
        if (rows.isEmpty()) {
            body.addView(emptyCard());
            return;
        }
        LocalDate today = LocalDate.now(COLOMBO);
        LinearLayout pair = null;
        for (int i = 0; i < rows.size(); i++) {
            if (i % 2 == 0) {
                pair = new LinearLayout(context());
                pair.setBaselineAligned(false);
                var params = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) params.topMargin = dp(12);
                body.addView(pair, params);
            }
            var bill = rows.get(i);
            LocalDate due = LocalDate.parse(bill.dueDate());
            long days = ChronoUnit.DAYS.between(today, due);
            String dueText = days <= 0 ? context().getString(R.string.bill_due_today)
                    : days == 1 ? context().getString(R.string.bill_due_tomorrow)
                    : context().getString(R.string.bill_due_on, due.format(DateTimeFormatter.ofPattern("d MMM", locale())));
            boolean soon = days <= 3;
            var card = card();
            var content = new LinearLayout(context());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(14), dp(14), dp(14), dp(14));
            TextView badge = new TextView(context());
            badge.setText(dueText);
            badge.setTextSize(12);
            badge.setTypeface(badge.getTypeface(), android.graphics.Typeface.BOLD);
            badge.setBackgroundResource(R.drawable.finora_badge);
            badge.setBackgroundTintList(ColorStateList.valueOf(context().getColor(
                    soon ? R.color.finora_error_surface : R.color.finora_neutral_surface)));
            badge.setTextColor(context().getColor(soon ? R.color.finora_error : R.color.finora_ink));
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            content.addView(badge, new LinearLayout.LayoutParams(-2, -2));
            TextView name = rowTitle(bill.name());
            name.setPadding(0, dp(10), 0, 0);
            content.addView(name);
            content.addView(caption(Money.rupees(context(), bill.amount())));
            card.addView(content);
            card.setFocusable(true);
            card.setContentDescription(bill.name() + ", " + context().getString(R.string.bill_details,
                    Money.amount(bill.amount()), bill.dueDate()));
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            var params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i % 2 == 0) params.setMarginEnd(dp(6));
            else params.setMarginStart(dp(6));
            pair.addView(card, params);
            if (i % 2 == 0 && i == rows.size() - 1) pair.addView(new View(context()), new LinearLayout.LayoutParams(0, 0, 1));
        }
    }

    private void renderRecent(LinearLayout body, List<lk.finora.app.data.repo.TransactionRepository.Row> rows) {
        if (rows.isEmpty()) {
            body.addView(emptyCard());
            return;
        }
        var categories = model.highlights.categories.value;
        LocalDate today = LocalDate.now(COLOMBO);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var category = TransactionRows.category(categories, row.categoryId());
            String day = TransactionRows.day(context(), row.txnDate(), today);
            String meta = category == null ? day : TransactionRows.displayName(context(), category) + " · " + day;
            var item = ItemTransactionRowBinding.inflate(LayoutInflater.from(context()), body, false);
            TransactionRows.bind(item, row, category, meta, TransactionRows.position(i, rows.size()));
            item.getRoot().setOnClickListener(ignored -> openRecords.run());
            body.addView(item.getRoot());
        }
    }

    private void renderGoal(LinearLayout body, List<lk.finora.app.data.api.GoalDto> rows) {
        var goal = DashboardHighlights.focus(rows);
        if (goal == null) {
            body.addView(caption(context().getText(R.string.records_empty)));
            return;
        }
        var top = new LinearLayout(context());
        top.setGravity(Gravity.CENTER_VERTICAL);
        var icon = new android.widget.ImageView(context());
        icon.setImageResource(R.drawable.finora_ic_cat_savings);
        icon.setBackgroundResource(R.drawable.finora_circle);
        icon.setBackgroundTintList(ColorStateList.valueOf(context().getColor(R.color.finora_green)));
        icon.setImageTintList(ColorStateList.valueOf(context().getColor(R.color.finora_action_ink)));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = rowTitle(goal.name());
        name.setTextSize(16);
        name.setPadding(dp(10), 0, dp(10), 0);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView percent = rowTitle(goal.progressPercent() + "%");
        percent.setTextSize(20);
        percent.setTextColor(context().getColor(R.color.finora_green));
        top.addView(percent);
        body.addView(top);
        var bar = new LinearProgressIndicator(context());
        bar.setTrackThickness(dp(10));
        bar.setTrackCornerRadius(dp(5));
        bar.setIndicatorColor(context().getColor(R.color.finora_green));
        bar.setTrackColor(context().getColor(R.color.finora_field));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setTrackStopIndicatorSize(0);
        bar.setIndicatorTrackGapSize(0);
        bar.setProgressCompat(Math.max(0, Math.min(100, goal.progressPercent())), false);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        params.bottomMargin = dp(8);
        body.addView(bar, params);
        body.addView(caption(context().getString(R.string.goal_progress_short, Money.rupees(context(), goal.saved()),
                Money.rupees(context(), goal.targetAmount()), Money.rupees(context(), goal.remaining()))));
        body.setFocusable(true);
        body.setContentDescription(goal.name() + ", " + context().getString(R.string.goal_progress_text,
                Money.amount(goal.saved()), Money.amount(goal.targetAmount()), Money.amount(goal.remaining()),
                goal.progressPercent()));
        for (int i = 0; i < body.getChildCount(); i++) {
            body.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private <T> Runnable bind(DashboardHighlights.Section<T> section, LinearLayout body,
                              BiConsumer<LinearLayout, T> renderValue) {
        Runnable renderSection = () -> {
            body.removeAllViews();
            body.setContentDescription(null);
            body.setFocusable(false);
            if (!section.work.isCurrent()) return;
            if (section.work.busy() || section.value == null && section.work.error == 0) {
                body.addView(padded(caption(context().getText(R.string.records_loading))));
            } else if (section.work.error != 0) {
                TextView error = caption(context().getText(section.work.error));
                error.setTextColor(context().getColor(R.color.finora_error));
                body.addView(padded(error));
                var retry = new MaterialButton(context(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
                retry.setText(R.string.records_retry);
                retry.setOnClickListener(ignored -> model.refresh());
                body.addView(retry);
            } else {
                renderValue.accept(body, section.value);
            }
        };
        section.work.changes.observe(activity, ignored -> renderSection.run());
        return renderSection;
    }

    private void setUpCharts() {
        PieChart pie = view.dashboardCategoryChart;
        style(pie);
        pie.setDrawHoleEnabled(true);
        pie.setHoleRadius(62f);
        pie.setTransparentCircleRadius(0f);
        pie.setHoleColor(android.graphics.Color.TRANSPARENT);
        pie.setDrawEntryLabels(false);
        pie.setCenterTextColor(context().getColor(R.color.finora_ink));
        pie.setCenterTextSize(12f);
        pie.setRotationEnabled(false);
        BarChart bars = view.dashboardTrendChart;
        style(bars);
        bars.getXAxis().setEnabled(false);
        bars.getAxisRight().setEnabled(false);
        var axis = bars.getAxisLeft();
        axis.setDrawLabels(false);
        axis.setDrawGridLines(false);
        axis.setDrawAxisLine(false);
        axis.setDrawZeroLine(true);
        axis.setZeroLineColor(context().getColor(R.color.finora_outline));
        axis.setAxisMinimum(-1.1f);
        axis.setAxisMaximum(1.1f);
        bars.setFitBars(true);
        bars.setDrawGridBackground(false);
    }

    private void style(Chart<?> chart) {
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setNoDataText("");
        // Exact labelled values beside each chart are the accessible equivalent.
        chart.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private View legendRow(int color, String name, String amount, String spoken) {
        var row = new LinearLayout(context());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.setFocusable(true);
        row.setContentDescription(spoken);
        var dot = new View(context());
        dot.setBackgroundResource(R.drawable.finora_circle);
        dot.setBackgroundTintList(ColorStateList.valueOf(color));
        var dotParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        dotParams.setMarginEnd(dp(8));
        row.addView(dot, dotParams);
        TextView label = caption(name);
        label.setTextColor(context().getColor(R.color.finora_ink));
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = caption(amount);
        value.setTextColor(context().getColor(R.color.finora_ink));
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(value);
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        return row;
    }

    private MaterialCardView card() {
        var card = new MaterialCardView(context());
        card.setCardBackgroundColor(context().getColor(R.color.finora_field));
        card.setStrokeColor(context().getColor(R.color.finora_card_line));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        card.setCardElevation(0);
        return card;
    }

    private View emptyCard() {
        var card = card();
        card.addView(padded(caption(context().getText(R.string.records_empty))));
        return card;
    }

    private View padded(View child) {
        child.setPadding(dp(16), dp(14), dp(16), dp(14));
        return child;
    }

    private TextView caption(CharSequence text) {
        TextView view = new TextView(context());
        view.setTextAppearance(R.style.FinoraText_Caption);
        view.setText(text);
        return view;
    }

    private TextView rowTitle(CharSequence text) {
        TextView view = new TextView(context());
        view.setTextAppearance(R.style.FinoraText_RowTitle);
        view.setText(text);
        return view;
    }

    private void status(TextView status, int message, boolean error) {
        lk.finora.app.ui.FinancialCard.showState(status, message, error);
    }

    private static void markHeadings(View root) {
        String tag = root.getContext().getString(R.string.finora_heading_tag);
        if (tag.equals(root.getTag())) ViewCompat.setAccessibilityHeading(root, true);
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) markHeadings(group.getChildAt(i));
        }
    }

    private boolean sinhala() { return "si".equals(locale().getLanguage()); }

    private Locale locale() { return context().getResources().getConfiguration().getLocales().get(0); }

    private String monthLabel(YearMonth value, String pattern) {
        return value.format(DateTimeFormatter.ofPattern(pattern, locale()));
    }

    private static int percent(BigDecimal current, BigDecimal maximum) {
        if (maximum.signum() <= 0) return 0;
        return current.multiply(BigDecimal.valueOf(100)).divide(maximum, 0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValue();
    }

    private static float ratio(BigDecimal value, BigDecimal max) {
        return max.signum() == 0 ? 0f : value.divide(max, MathContext.DECIMAL32).floatValue();
    }

    private int dp(int value) {
        return Math.round(value * context().getResources().getDisplayMetrics().density);
    }
}
