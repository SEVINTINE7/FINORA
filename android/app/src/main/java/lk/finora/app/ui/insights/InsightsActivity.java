package lk.finora.app.ui.insights;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import lk.finora.app.R;
import lk.finora.app.data.repo.InsightsRepository.Insight;
import lk.finora.app.data.repo.InsightsRepository.Part;
import lk.finora.app.databinding.ActivityFinoraInsightsBinding;
import lk.finora.app.databinding.ItemInsightBinding;
import lk.finora.app.databinding.ItemScorePartBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.advice.AdviceActivity;

/** T66.2/D65/D70 Insights: score first (ring and five parts), then one card per spending pattern. */
public final class InsightsActivity extends SecureActivity {
    private InsightsViewModel model;
    private ActivityFinoraInsightsBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(InsightsViewModel.class);
        if (!AccountListActivity.verify(this, model.patternsWork)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityFinoraInsightsBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        for (View heading : new View[]{view.insightsTitle, view.insightsPartsHeading, view.insightsPatternsHeading}) {
            ViewCompat.setAccessibilityHeading(heading, true);
        }
        view.insightsSwitch.switchPrevious.setOnClickListener(ignored -> model.shift(-1));
        view.insightsSwitch.switchNext.setOnClickListener(ignored -> model.shift(1));
        view.insightsRetry.setOnClickListener(ignored -> model.load());
        view.insightsUpdate.setOnClickListener(ignored -> {
            if (!model.busy() && model.patternsWork.isCurrent()) {
                startActivity(new Intent(this, ScoreDetailsActivity.class)
                        .putExtra(ScoreDetailsActivity.MONTH, model.month.toString()));
            }
        });
        view.insightsAdvice.setOnClickListener(ignored -> {
            if (model.patternsWork.isCurrent()) {
                startActivity(new Intent(this, AdviceActivity.class).putExtra(AdviceActivity.MONTH, model.month.toString()));
            }
        });
        for (Work work : new Work[]{model.patternsWork, model.scoreWork}) {
            work.changes.observe(this, ignored -> render());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.patternsWork)) model.load();
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private void render() {
        if (!AccountListActivity.verify(this, model.patternsWork)) return;
        String month = model.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale()));
        view.insightsSwitch.switchLabel.setText(month);
        boolean busy = model.busy();
        view.insightsSwitch.switchPrevious.setEnabled(!busy && model.month.minusMonths(1).getYear() >= 1000);
        view.insightsSwitch.switchNext.setEnabled(!busy && model.month.plusMonths(1).getYear() <= 9999);
        view.insightsUpdate.setEnabled(!busy);
        view.insightsAdviceText.setText(getString(R.string.insight_see_advice, month));

        int error = 0;
        for (Work work : new Work[]{model.patternsWork, model.scoreWork}) if (error == 0) error = work.error;
        view.insightsStatus.setVisibility(busy || error != 0 ? View.VISIBLE : View.GONE);
        view.insightsStatus.setText(busy ? getString(R.string.records_loading) : error != 0 ? getString(error) : "");
        view.insightsRetry.setVisibility(!busy && error != 0 ? View.VISIBLE : View.GONE);

        renderScore();
        renderPatterns();
    }

    private void renderScore() {
        var score = model.score;
        boolean shown = score != null;
        view.insightsScoreCard.setVisibility(shown || model.scoreWork.busy() ? View.VISIBLE : View.GONE);
        view.insightsPartsHeader.setVisibility(shown ? View.VISIBLE : View.GONE);
        view.insightsPartsCard.setVisibility(shown ? View.VISIBLE : View.GONE);
        view.insightsParts.removeAllViews();
        if (!shown) {
            view.insightsScoreRing.setProgressCompat(0, false);
            view.insightsScoreValue.setText("");
            view.insightsScoreText.setText(R.string.records_loading);
            view.insightsScoreSummary.setContentDescription(getString(R.string.score_heading));
            return;
        }
        String text = score.total() == null ? getString(R.string.score_unavailable)
                : getString(R.string.score_total, score.total().toString());
        view.insightsScoreRing.setProgressCompat(score.total() == null ? 0 : score.total(), false);
        view.insightsScoreValue.setText(score.total() == null ? "–" : score.total().toString());
        view.insightsScoreText.setText(text);
        view.insightsScoreSummary.setContentDescription(getString(R.string.score_heading) + ". " + text);
        view.insightsPartsCount.setText(getResources().getQuantityString(R.plurals.score_parts_count,
                score.parts().length, score.parts().length));
        for (int i = 0; i < score.parts().length; i++) showPart(score.parts()[i], i == 0);
    }

    private void showPart(Part part, boolean first) {
        var row = ItemScorePartBinding.inflate(getLayoutInflater(), view.insightsParts, false);
        row.partDivider.setVisibility(first ? View.GONE : View.VISIBLE);
        String name = getString(InsightRules.partName(part.key()));
        row.partName.setText(name);
        String weight = Integer.toString(part.weight());
        String spokenPoints;
        if (part.points() != null) {
            String points = InsightRules.points(part.points());
            row.partPoints.setText(getString(R.string.score_part_points, points, weight));
            spokenPoints = getString(R.string.score_part_spoken, name, points, weight);
        } else {
            int status = "NOT_APPLICABLE".equals(part.status()) ? R.string.score_not_applicable : R.string.score_unavailable;
            row.partPoints.setText("NOT_APPLICABLE".equals(part.status()) ? getString(status)
                    : getString(R.string.score_part_points, "–", weight));
            row.partPoints.setTextColor(getColor(R.color.finora_muted));
            spokenPoints = name + ": " + getString(status);
        }
        row.partBar.setProgressCompat(InsightRules.partProgress(part.points(), part.weight()), false);
        row.partReason.setText(InsightRules.reason(part.reasonKey()));
        String facts = part.points() == null ? null : facts(part);
        row.partFacts.setVisibility(facts == null ? View.GONE : View.VISIBLE);
        row.partFacts.setText(facts);
        row.partRow.setContentDescription(spokenPoints + " " + row.partReason.getText() + (facts == null ? "" : " " + facts));
        view.insightsParts.addView(row.getRoot());
    }

    private String facts(Part part) {
        Map<String, BigDecimal> facts = part.facts();
        return switch (part.key()) {
            case "SPENDING_CONTROL" -> money(facts, R.string.score_fact_spending, "income", "consumption");
            case "DEBT_PAYMENTS", "GOAL_PROGRESS" -> money(facts, R.string.score_fact_progress, "credited", "target");
            case "SAVINGS" -> facts.get("supported") == null || facts.get("targetRate") == null
                    ? getString(R.string.records_failed)
                    : getString(R.string.score_fact_savings, Money.rupees(this, facts.get("supported")),
                            InsightRules.percent(facts.get("targetRate"), locale()));
            default -> money(facts, R.string.score_fact_emergency, "reserve", "monthlyNeeds");
        };
    }

    private String money(Map<String, BigDecimal> facts, int label, String... keys) {
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            BigDecimal value = facts.get(keys[i]);
            if (value == null) return getString(R.string.records_failed);
            values[i] = Money.rupees(this, value);
        }
        return getString(label, values);
    }

    private void renderPatterns() {
        view.insightsResults.removeAllViews();
        var rows = model.patterns;
        int shown = 0;
        if (rows != null) for (Insight row : rows) if (showInsight(row)) shown++;
        boolean known = rows != null;
        view.insightsPatternsHeader.setVisibility(known ? View.VISIBLE : View.GONE);
        view.insightsPatternsCount.setText(getResources().getQuantityString(R.plurals.insight_count, shown, shown));
        view.insightsEmpty.setVisibility(known && shown == 0 ? View.VISIBLE : View.GONE);
        view.insightsAdvice.setVisibility(known ? View.VISIBLE : View.GONE);
    }

    private boolean showInsight(Insight row) {
        int title = InsightRules.title(row.type());
        if (title == 0) return false;
        var card = ItemInsightBinding.inflate(getLayoutInflater(), view.insightsResults, false);
        card.insightIcon.setImageResource(InsightRules.icon(row.type()));
        card.insightTitle.setText(title);
        ViewCompat.setAccessibilityHeading(card.insightTitle, true);
        card.insightPeriod.setText(getString(R.string.insight_period, date(row.from()), date(row.to())));
        Map<String, BigDecimal> facts = row.facts();
        String[] keys = InsightRules.shareKeys(row.type());
        if (keys != null) {
            BigDecimal amount = facts.get(keys[0]);
            BigDecimal ratio = facts.get(keys[1]);
            BigDecimal limit = facts.get("threshold");
            if (amount == null || ratio == null) {
                card.insightText.setText(R.string.records_failed);
            } else {
                String share = InsightRules.percent(ratio, locale());
                card.insightText.setText(getString(InsightRules.shareText(row.type()), Money.rupees(this, amount), share));
                var bar = new LimitBar(this);
                bar.set(ratio.floatValue(), limit == null ? -1 : limit.floatValue());
                bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.insightChart.addView(bar, new ViewGroup.LayoutParams(-1, -2));
                if (limit != null) {
                    String limitText = InsightRules.percent(limit, locale());
                    card.insightChip.setText(getString(R.string.insight_above, limitText));
                    card.insightChart.addView(scale(limitText, limit.floatValue()));
                    card.insightChart.setContentDescription(getString(R.string.insight_share_spoken, share, limitText));
                } else {
                    card.insightChip.setVisibility(View.GONE);
                }
            }
            if ("PAYDAY_SPENDING".equals(row.type())) {
                card.insightPeriod.setText(getString(R.string.insight_note_period, getString(R.string.insight_payday_help),
                        getString(R.string.insight_period, date(row.from()), date(row.to()))));
            }
        } else {
            BigDecimal total = facts.get("weekendSpend");
            BigDecimal weekend = facts.get("weekendDailyAverage");
            BigDecimal weekday = facts.get("weekdayDailyAverage");
            String times = InsightRules.times(facts);
            if (total == null || weekend == null || weekday == null) {
                card.insightText.setText(R.string.records_failed);
                card.insightChip.setVisibility(View.GONE);
            } else {
                card.insightText.setText(getString(R.string.insight_weekend_values, Money.rupees(this, total),
                        Money.rupees(this, weekend), Money.rupees(this, weekday)));
                if (times != null) card.insightChip.setText(getString(R.string.insight_times, times));
                else card.insightChip.setVisibility(View.GONE);
                card.insightChart.addView(pair(weekend, weekday));
                card.insightChart.setContentDescription(getString(R.string.insight_weekend_spoken,
                        Money.rupees(this, weekend), Money.rupees(this, weekday)));
            }
        }
        view.insightsResults.addView(card.getRoot());
        return true;
    }

    /** 0% · limit · 100% labels under a share bar. */
    private View scale(String limit, float position) {
        var row = new android.widget.FrameLayout(this);
        var params = new android.widget.FrameLayout.LayoutParams(-1, -2);
        params.topMargin = dp(22);
        row.setLayoutParams(params);
        row.addView(small("0%", Gravity.START), new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.START));
        row.addView(small("100%", Gravity.END), new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.END));
        TextView marker = small(getString(R.string.insight_limit, limit), Gravity.CENTER);
        row.addView(marker, new android.widget.FrameLayout.LayoutParams(-2, -2));
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float x = (r - l) * position - marker.getWidth() / 2f;
            if (getWindow().getDecorView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                x = (r - l) * (1 - position) - marker.getWidth() / 2f;
            }
            marker.setX(Math.max(0, Math.min(x, (r - l) - marker.getWidth())));
        });
        return row;
    }

    /** Two bars: weekend daily average and weekday daily average, scaled to the larger. */
    private View pair(BigDecimal weekend, BigDecimal weekday) {
        var row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(24), 0, dp(24), 0);
        BigDecimal max = weekend.max(weekday);
        row.addView(column(getString(R.string.insight_weekend_day), weekend, max, R.color.finora_chart_3),
                new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(column(getString(R.string.insight_weekday), weekday, max, R.color.finora_chart_5),
                new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View column(String label, BigDecimal value, BigDecimal max, int color) {
        var column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        TextView amount = small(Money.rupees(this, value), Gravity.CENTER);
        amount.setTextColor(getColor(R.color.finora_ink));
        amount.setTypeface(null, android.graphics.Typeface.BOLD);
        column.addView(amount);
        var bar = new View(this);
        var shape = new android.graphics.drawable.GradientDrawable();
        float radius = dp(8);
        shape.setCornerRadii(new float[]{radius, radius, radius, radius, dp(3), dp(3), dp(3), dp(3)});
        shape.setColor(getColor(color));
        bar.setBackground(shape);
        int height = max.signum() <= 0 ? 0 : value.multiply(BigDecimal.valueOf(dp(96)))
                .divide(max, 0, java.math.RoundingMode.HALF_UP).intValue();
        var params = new LinearLayout.LayoutParams(dp(56), Math.max(dp(4), height));
        params.topMargin = dp(4);
        column.addView(bar, params);
        TextView name = small(label, Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        column.addView(name);
        return column;
    }

    private TextView small(String text, int gravity) {
        var label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setGravity(gravity);
        label.setTextColor(getColor(R.color.finora_muted));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return label;
    }

    private String date(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale()));
        } catch (RuntimeException invalid) {
            return iso;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
