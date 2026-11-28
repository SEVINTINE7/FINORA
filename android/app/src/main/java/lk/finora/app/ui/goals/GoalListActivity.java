package lk.finora.app.ui.goals;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.databinding.ActivityGoalListBinding;
import lk.finora.app.databinding.ItemGoalBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.planning.PlanningActivity;

/** T64.2/D65 savings goals: summary of goals in progress and progress cards, focus goal first (T19.3, D68). */
public final class GoalListActivity extends SecureActivity {
    private GoalListViewModel model;
    private ActivityGoalListBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityGoalListBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.goalHeading, true);
        view.recordsAdd.setOnClickListener(ignored -> {
            if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
                startActivity(new Intent(this, GoalFormActivity.class));
            }
        });
        view.goalSimulate.setOnClickListener(ignored -> {
            if (AccountListActivity.verify(this, model.work)) startActivity(PlanningActivity.simulation(this));
        });
        view.recordsRetry.setOnClickListener(ignored -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        boolean failed = !model.work.busy() && model.work.error != 0;
        view.recordsRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        renderSummary();
        view.recordsRows.removeAllViews();
        GoalDto focus = GoalListViewModel.focus(model.rows);
        for (GoalDto goal : GoalListViewModel.ordered(model.rows)) {
            view.recordsRows.addView(row(goal, goal == focus));
        }
    }

    private void renderSummary() {
        var totals = GoalListViewModel.totals(model.rows);
        view.goalSummaryCard.setVisibility(totals.count() == 0 ? View.GONE : View.VISIBLE);
        if (totals.count() == 0) return;
        String amount = Money.rupees(this, totals.saved());
        String count = getResources().getQuantityString(R.plurals.goal_summary_count, totals.count(),
                totals.count(), Money.rupees(this, totals.toGo()));
        view.goalSummaryAmount.setText(amount);
        view.goalSummaryCount.setText(count);
        plainBar(view.goalSummaryBar, totals.percent());
        view.goalSummaryCard.setContentDescription(getString(R.string.goal_summary_label) + ", " + amount + ". "
                + getString(R.string.budget_used_of, amount, Money.rupees(this, totals.target())) + ". " + count);
    }

    private View row(GoalDto goal, boolean focus) {
        var card = ItemGoalBinding.inflate(LayoutInflater.from(this), view.recordsRows, false);
        String status = goal.status();
        card.recordName.setText(goal.name());
        card.goalFocus.setVisibility(focus ? View.VISIBLE : View.GONE);
        card.recordDetails.setText(getString(R.string.budget_used_of, Money.rupees(this, goal.saved()),
                Money.rupees(this, goal.targetAmount())));
        card.goalIcon.setBackgroundTintList(ColorStateList.valueOf(getColor("CANCELLED".equals(status)
                ? R.color.finora_neutral_surface : R.color.finora_success_surface)));
        card.goalIcon.setImageTintList(ColorStateList.valueOf(getColor("CANCELLED".equals(status)
                ? R.color.finora_muted : R.color.finora_green)));
        statusChip(this, card.goalStatus, status);
        card.goalProgress.setIndicatorColor(getColor(GoalRules.statusBar(status)));
        plainBar(card.goalProgress, goal.progressPercent());
        card.goalMeta.setText(meta(this, goal));

        // TalkBack: the old exact LKR sentence, focus and status as the state, open as the click action.
        card.goalOpen.setContentDescription(goal.name() + ". " + sentence(this, goal));
        String spokenState = getString(GoalRules.statusLabel(status));
        if (focus) spokenState = getString(R.string.goal_focus_spoken) + ", " + spokenState;
        ViewCompat.setStateDescription(card.goalOpen, spokenState);
        ViewCompat.replaceAccessibilityAction(card.goalOpen, AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.goal_open_named, goal.name()), null);
        card.goalOpen.setOnClickListener(ignored -> {
            if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
                Intent open = new Intent(this, GoalDetailActivity.class)
                        .putExtra(AccountListActivity.ITEM_ID, goal.id());
                if (focus) open.putExtra(GoalDetailActivity.FOCUS_ID, goal.id());
                startActivity(open);
            }
        });
        return card.getRoot();
    }

    /** Status chip: tinted fill, word and icon (shared with the detail screen). */
    static void statusChip(Context context, TextView chip, String status) {
        int ink = context.getColor(GoalRules.statusColor(status));
        chip.setText(GoalRules.statusLabel(status));
        chip.setTextColor(ink);
        chip.setBackgroundTintList(ColorStateList.valueOf(context.getColor(GoalRules.statusFill(status))));
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(GoalRules.statusIcon(status), 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(ink));
    }

    /** "53% · Rs. 56,000.00 to go · by 31 Mar 2027", "100% · reached" or "14% · saved amounts kept". */
    static String meta(Context context, GoalDto goal) {
        String percent = percent(context, goal.progressPercent());
        if ("DONE".equals(goal.status())) return context.getString(R.string.goal_meta_reached, percent);
        if ("CANCELLED".equals(goal.status())) return context.getString(R.string.goal_meta_kept, percent);
        String text = context.getString(R.string.goal_meta_to_go, percent, Money.rupees(context, goal.remaining()));
        String deadline = goal.targetDate() == null ? context.getString(R.string.goal_no_deadline_short)
                : context.getString(R.string.goal_by_date, readableDate(goal.targetDate()));
        return text + " · " + deadline;
    }

    /** The spoken sentence kept from T19.3: "Saved LKR X of LKR Y (P%) · LKR Z to go · by <date>". */
    static String sentence(Context context, GoalDto goal) {
        String text = context.getString(R.string.goal_progress_text, GoalRules.plain(goal.saved()),
                GoalRules.plain(goal.targetAmount()), GoalRules.plain(goal.remaining()), goal.progressPercent());
        if (goal.targetDate() != null) {
            text = text + " · " + context.getString(R.string.goal_by_date, readableDate(goal.targetDate()));
        }
        return text;
    }

    static void plainBar(LinearProgressIndicator bar, int percent) {
        bar.setTrackStopIndicatorSize(0);
        bar.setIndicatorTrackGapSize(0);
        bar.setProgressCompat(Math.max(0, Math.min(100, percent)), false);
    }

    static String percent(Context context, int value) {
        var locale = context.getResources().getConfiguration().getLocales().get(0);
        return NumberFormat.getPercentInstance(locale).format(BigDecimal.valueOf(value).movePointLeft(2));
    }

    static String readableDate(String isoDate) {
        return LocalDate.parse(isoDate)
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
    }
}
