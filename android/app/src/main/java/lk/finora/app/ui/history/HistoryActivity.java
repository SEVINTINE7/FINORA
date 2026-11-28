package lk.finora.app.ui.history;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.MonthlyRepository;
import lk.finora.app.data.repo.MonthlyRepository.Month;
import lk.finora.app.databinding.ActivityHistoryBinding;
import lk.finora.app.databinding.ItemHistoryMonthBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.insights.InsightRules;

/** T66.3/D65/D70 Monthly history: range toggle, score trend with a summary in words, month cards newest first. */
public final class HistoryActivity extends SecureActivity {
    private static final int[] RANGES = {6, 12, 24};
    private Model model;
    private ActivityHistoryBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(Model.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.historyHeading, true);
        ViewCompat.setAccessibilityHeading(view.historyChartHeading, true);
        ViewCompat.setAccessibilityHeading(view.historyMonthsHeading, true);
        View[] buttons = {view.historyRange6, view.historyRange12, view.historyRange24};
        for (int i = 0; i < RANGES.length; i++) {
            ((TextView) buttons[i]).setText(getString(R.string.history_range, Integer.toString(RANGES[i])));
            // Large font sizes: shrink the label to fit instead of cutting it off.
            var button = (com.google.android.material.button.MaterialButton) buttons[i];
            button.setMaxLines(1);
            button.setPaddingRelative(dp(4), button.getPaddingTop(), dp(4), button.getPaddingBottom());
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 11,
                    Math.round(button.getTextSize() / getResources().getDisplayMetrics().scaledDensity), 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        view.historyRange.check(buttons[indexOf(model.count)].getId());
        view.historyRange.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].getId() == id && model.count != RANGES[i]) model.range(RANGES[i]);
            }
        });
        view.historySwitch.switchPrevious.setOnClickListener(ignored -> model.shift(-1));
        view.historySwitch.switchNext.setOnClickListener(ignored -> model.shift(1));
        view.historyRetry.setOnClickListener(ignored -> model.load());
        styleChart();
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.work)) model.load();
    }

    private static int indexOf(int count) {
        for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == count) return i;
        return 0;
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private String shortMonth(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("LLL", locale()));
    }

    private String longMonth(YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale()));
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        YearMonth start = model.end.minusMonths(model.count - 1L);
        view.historySwitch.switchLabel.setText(getString(R.string.history_period, longMonth(start), longMonth(model.end)));
        view.historySwitch.switchPrevious.setEnabled(!busy);
        view.historySwitch.switchNext.setEnabled(!busy
                && model.end.isBefore(YearMonth.now(ZoneId.of("Asia/Colombo")).minusMonths(1)));
        for (int i = 0; i < view.historyRange.getChildCount(); i++) view.historyRange.getChildAt(i).setEnabled(!busy);
        int message = busy ? R.string.records_loading : model.work.error;
        view.historyStatus.setText(message == 0 ? "" : getString(message));
        view.historyStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        boolean pending = model.rows != null && java.util.Arrays.stream(model.rows).anyMatch(Month::refreshPending);
        view.historyRetry.setVisibility(!busy && (model.work.error != 0 || pending) ? View.VISIBLE : View.GONE);

        view.historyRows.removeAllViews();
        boolean loaded = !busy && model.rows != null;
        boolean any = loaded && model.rows.length > 0;
        view.historyEmpty.setVisibility(loaded && !any ? View.VISIBLE : View.GONE);
        view.historyMonthsHeader.setVisibility(any ? View.VISIBLE : View.GONE);
        view.historyChartCard.setVisibility(View.GONE);
        if (!any) return;
        showTrend(start);
        for (Month row : HistoryRules.newestFirst(model.rows)) showMonth(row);
    }

    private void styleChart() {
        var chart = view.historyChart;
        chart.setTouchEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("");
        chart.getAxisRight().setEnabled(false);
        var left = chart.getAxisLeft();
        left.setAxisMinimum(0);
        left.setAxisMaximum(100);
        left.setLabelCount(3, true);
        left.setDrawAxisLine(false);
        left.setGridColor(getColor(R.color.finora_card_line));
        left.setTextColor(getColor(R.color.finora_muted));
        var x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(false);
        x.setGranularity(1f);
        x.setTextColor(getColor(R.color.finora_muted));
        chart.setExtraBottomOffset(4);
    }

    private void showTrend(YearMonth start) {
        var data = new LineData();
        var points = new ArrayList<Entry>();
        YearMonth last = null;
        StringBuilder spoken = new StringBuilder(getString(R.string.history_chart)).append(". ");
        for (Month row : model.rows) {
            YearMonth current = YearMonth.parse(row.month());
            boolean scored = !row.refreshPending() && row.score() != null;
            if (!scored || (last != null && !current.equals(last.plusMonths(1)))) {
                addSegment(data, points);
                points = new ArrayList<>();
            }
            if (scored) points.add(new Entry(HistoryRules.position(start, row), row.score()));
            spoken.append(scored ? getString(R.string.history_point, longMonth(current), row.score().toString())
                    : getString(R.string.history_point_none, longMonth(current))).append(". ");
            last = current;
        }
        addSegment(data, points);
        if (data.getDataSetCount() == 0) return;
        var chart = view.historyChart;
        var x = chart.getXAxis();
        x.setAxisMinimum(-0.3f);
        x.setAxisMaximum(model.count - 0.7f);
        x.setLabelCount(Math.min(model.count, 6));
        x.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return shortMonth(start.plusMonths(Math.round(value)));
            }
        });
        chart.setData(data);
        chart.invalidate();
        chart.setContentDescription(spoken.toString().trim());
        var ends = HistoryRules.ends(model.rows);
        String summary = ends == null ? "" : getString(R.string.history_summary,
                longMonth(YearMonth.parse(ends[0].month())), ends[0].score(),
                longMonth(YearMonth.parse(ends[1].month())), ends[1].score());
        int gaps = HistoryRules.gaps(model.rows);
        if (gaps > 0) summary = (summary + " " + getResources().getQuantityString(R.plurals.history_gaps, gaps, gaps)).trim();
        view.historySummary.setText(summary);
        view.historySummary.setVisibility(summary.isEmpty() ? View.GONE : View.VISIBLE);
        view.historyChartCard.setVisibility(View.VISIBLE);
    }

    private void addSegment(LineData data, ArrayList<Entry> points) {
        if (points.isEmpty()) return;
        var set = new LineDataSet(points, "");
        set.setColor(getColor(R.color.finora_green));
        set.setLineWidth(2.5f);
        set.setCircleColor(getColor(R.color.finora_green));
        set.setCircleRadius(4.5f);
        set.setCircleHoleRadius(2.5f);
        set.setCircleHoleColor(getColor(R.color.finora_field));
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
        data.addDataSet(set);
    }

    private void showMonth(Month row) {
        var card = ItemHistoryMonthBinding.inflate(getLayoutInflater(), view.historyRows, false);
        YearMonth month = YearMonth.parse(row.month());
        card.monthName.setText(longMonth(month));
        ViewCompat.setAccessibilityHeading(card.monthName, true);
        boolean scored = !row.refreshPending() && row.score() != null;
        card.monthScoreText.setText(row.refreshPending() ? getString(R.string.history_refresh_pending)
                : scored ? getString(R.string.history_score, row.score().toString()) : getString(R.string.history_no_score));
        card.monthBadgeValue.setText(scored ? row.score().toString() : "–");
        card.monthBadge.setBackgroundTintList(ColorStateList.valueOf(getColor(scored
                ? R.color.finora_success_surface : R.color.finora_neutral_surface)));
        int ink = getColor(scored ? R.color.finora_green : R.color.finora_muted);
        card.monthBadgeValue.setTextColor(ink);
        card.monthBadgeCaption.setTextColor(ink);
        if (!row.refreshPending()) {
            card.monthAmounts.addView(amount(R.string.category_income, R.drawable.finora_ic_arrow_up,
                    Money.signed(this, row.income(), true)));
            card.monthAmounts.addView(amount(R.string.category_expense, R.drawable.finora_ic_arrow_down,
                    Money.signed(this, row.expense(), false)));
            card.monthAmounts.addView(amount(R.string.history_net, 0, Money.net(this, row.net())));
            for (var pattern : row.insights()) {
                int label = InsightRules.title(pattern.type());
                if (label != 0) card.monthPatterns.addView(chip(getString(label)));
            }
        } else {
            card.monthAmounts.setVisibility(View.GONE);
        }
        card.monthPatterns.setVisibility(card.monthPatterns.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        if (row.reconstructedAt() != null && !row.refreshPending()) {
            card.monthNote.setText(R.string.history_reconstructed);
            card.monthNote.setVisibility(View.VISIBLE);
        }
        view.historyRows.addView(card.getRoot());
    }

    private View amount(int label, int icon, String value) {
        var column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        var name = new TextView(this);
        name.setText(label);
        name.setTextSize(12);
        name.setTextColor(getColor(R.color.finora_muted));
        name.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != 0) {
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            name.setCompoundDrawablePadding(dp(4));
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(name,
                    ColorStateList.valueOf(getColor(R.color.finora_muted)));
            var drawable = name.getCompoundDrawablesRelative()[0];
            if (drawable != null) drawable.setBounds(0, 0, dp(12), dp(12));
            name.setCompoundDrawablesRelative(drawable, null, null, null);
        }
        var number = new TextView(this);
        number.setText(value);
        number.setTextSize(14);
        number.setTextColor(getColor(R.color.finora_ink));
        number.setTypeface(null, android.graphics.Typeface.BOLD);
        column.addView(name);
        column.addView(number);
        column.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return column;
    }

    private TextView chip(String text) {
        var label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(getColor(R.color.finora_warn_ink));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMinHeight(dp(26));
        label.setPadding(dp(10), 0, dp(10), 0);
        label.setBackgroundResource(R.drawable.finora_badge);
        label.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.finora_warn_fill)));
        return label;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

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
