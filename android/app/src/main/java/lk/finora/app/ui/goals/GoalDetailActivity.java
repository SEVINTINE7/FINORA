package lk.finora.app.ui.goals;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;

import lk.finora.app.R;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.databinding.ActivityGoalDetailBinding;
import lk.finora.app.databinding.ItemContributionBinding;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.transactions.TransactionRows;

/**
 * T64.2/D65 one goal: progress ring, add a contribution (date from the calendar, D68), money saved so far with
 * remove, edit/cancel/resume/delete (T19.3).
 */
public final class GoalDetailActivity extends SecureActivity {
    private GoalDetailViewModel model;
    private ActivityGoalDetailBinding view;

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalDetailViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityGoalDetailBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        for (View heading : new View[]{view.goalName, view.goalAddHeading, view.goalHistoryHeading}) {
            ViewCompat.setAccessibilityHeading(heading, true);
        }
        lk.finora.app.ui.accounts.AccountFormActivity.watch(view.contributionAmount, ignored -> {});
        view.contributionDate.setOnClickListener(ignored -> {
            if (!canSelectDate()) return;
            var current = GoalRules.parseDate(model.contributionDate);
            FinancialDatePicker.show(this, R.string.goal_contribution_date,
                    current == null ? GoalRules.today() : current, this::selectDate);
        });
        FinancialDatePicker.restore(this, this::selectDate);
        model.id = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0);
        showDate();

        view.contributionAdd.setOnClickListener(ignored -> addContribution());
        view.recordsRetry.setOnClickListener(ignored -> {
            // After an unclear save, reloading shows whether the contribution is in the history
            model.work.ambiguousCreate = false;
            model.load(model.id);
        });
        view.goalEdit.setOnClickListener(ignored -> {
            if (!model.work.busy()) {
                startActivity(new Intent(this, GoalFormActivity.class)
                        .putExtra(AccountListActivity.ITEM_ID, model.id));
            }
        });
        view.goalCancel.setOnClickListener(ignored -> toggleCancelled());
        view.goalDelete.setOnClickListener(ignored -> AccountListActivity.confirm(this, model.work,
                R.string.goal_delete_message, model::delete));
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work) && !model.work.ambiguousCreate) model.load(model.id);
    }

    private boolean canSelectDate() {
        return AccountListActivity.verify(this, model.work) && !model.work.busy() && model.goal != null;
    }

    private void selectDate(LocalDate value) {
        if (!canSelectDate()) return;
        model.contributionDate = value.toString();
        view.contributionDateError.setVisibility(View.GONE);
        showDate();
    }

    /** "Today" or the chosen date; TalkBack hears "Date saved: <date>. Change date". */
    private void showDate() {
        LocalDate chosen = GoalRules.parseDate(model.contributionDate);
        String label = chosen == null ? getString(R.string.transaction_choose_date)
                : chosen.equals(GoalRules.today()) ? getString(R.string.goal_today)
                : TransactionRows.fullDate(this, chosen);
        view.contributionDate.setText(label);
        String spoken = chosen == null ? label : TransactionRows.fullDate(this, chosen);
        view.contributionDate.setContentDescription(getString(R.string.goal_date_spoken,
                getString(R.string.goal_contribution_date), spoken));
    }

    private void addContribution() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy() || model.goal == null) return;
        BigDecimal value;
        try {
            value = GoalRules.parseAmount(view.contributionAmount.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(view.contributionAmount, R.string.budget_limit_invalid);
            return;
        }
        String day = model.contributionDate;
        if (!GoalRules.validContributionDate(day, GoalRules.today())) {
            view.contributionDateError.setText(R.string.goal_contribution_date_invalid);
            view.contributionDateError.setVisibility(View.VISIBLE);
            return;
        }
        model.addContribution(value, day);
    }

    private void toggleCancelled() {
        GoalDto goal = model.goal;
        if (goal == null || model.work.busy()) return;
        if (!GoalRules.canEditAsIs(goal.targetDate(), GoalRules.today())) {
            // D16 refuses any edit while the stored target date is not in the future
            showSecure(new AlertDialog.Builder(this).setMessage(R.string.goal_date_passed)
                    .setPositiveButton(android.R.string.ok, null).create());
            return;
        }
        boolean cancel = !"CANCELLED".equals(goal.status());
        if (!cancel) {
            model.setCancelled(false);
            return;
        }
        showSecure(new AlertDialog.Builder(this).setMessage(R.string.goal_cancel_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.goal_cancel, (dialog, which) -> model.setCancelled(true))
                .create());
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.deleted) { finish(); return; }
        if (model.contributionAdded) {
            model.contributionAdded = false;
            view.contributionAmount.getText().clear();
        }
        boolean busy = model.work.busy();
        GoalDto goal = model.goal;
        if (goal != null) renderGoal(goal);
        boolean ready = goal != null && !busy;
        view.contributionAdd.setEnabled(ready && !model.work.ambiguousCreate);
        view.contributionDate.setEnabled(ready);
        view.goalEdit.setEnabled(ready);
        view.goalCancel.setEnabled(ready);
        view.goalDelete.setEnabled(ready);

        boolean failed = !busy && model.work.error != 0;
        view.recordsRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        view.recordsRetry.setEnabled(failed);
        view.recordsRetry.setText(model.work.ambiguousCreate ? R.string.records_reconcile : R.string.records_retry);
        int message = busy ? R.string.records_loading : model.work.error;
        view.recordsStatus.setText(message == 0 ? "" : getString(message));
        view.recordsStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);

        renderHistory(goal, busy);
    }

    private void renderGoal(GoalDto goal) {
        String status = goal.status();
        boolean focus = isFocus(goal);
        view.goalName.setText(goal.name());
        view.goalPercent.setText(GoalListActivity.percent(this, goal.progressPercent()));
        view.goalProgress.setIndicatorColor(getColor("CANCELLED".equals(status)
                ? R.color.finora_chart_5 : R.color.finora_mint));
        view.goalProgress.setProgressCompat(Math.max(0, Math.min(100, goal.progressPercent())), false);
        view.goalFocus.setVisibility(focus ? View.VISIBLE : View.GONE);
        GoalListActivity.statusChip(this, view.goalStatus, status);
        String saved = Money.rupees(this, goal.saved());
        String of = getString(R.string.goal_of_target, Money.rupees(this, goal.targetAmount()));
        view.goalSaved.setText(saved);
        view.goalDetails.setText(of);
        String spokenState = getString(GoalRules.statusLabel(status));
        if (focus) spokenState = getString(R.string.goal_focus_spoken) + ", " + spokenState;
        view.goalHero.setContentDescription(goal.name() + ". " + GoalListActivity.sentence(this, goal));
        ViewCompat.setStateDescription(view.goalHero, spokenState);

        String toGo = Money.rupees(this, goal.remaining());
        view.goalToGo.setText(toGo);
        view.goalToGoTile.setContentDescription(getString(R.string.goal_still_to_save) + ", " + toGo);
        String date = goal.targetDate() == null ? getString(R.string.goal_no_deadline)
                : GoalListActivity.readableDate(goal.targetDate());
        view.goalDateValue.setText(date);
        view.goalDateTile.setContentDescription(getString(R.string.goal_target_date) + ", " + date);
        view.goalCancel.setText("CANCELLED".equals(status) ? R.string.goal_resume : R.string.goal_cancel);
    }

    /**
     * D68 focus mark. Choosing the focus goal needs every goal, so the list passes the focus id it showed; a goal
     * cancelled here loses the mark. Opened without that id, no badge is shown.
     */
    private boolean isFocus(GoalDto goal) {
        return getIntent().getLongExtra(FOCUS_ID, 0) == goal.id() && "ACTIVE".equals(goal.status());
    }

    static final String FOCUS_ID = "goal_focus_id";

    private void renderHistory(GoalDto goal, boolean busy) {
        int count = model.contributions.size();
        boolean empty = goal != null && count == 0;
        view.goalNoContributions.setVisibility(empty ? View.VISIBLE : View.GONE);
        view.goalHistoryCard.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        view.goalHistoryCount.setText(count == 0 ? ""
                : getResources().getQuantityString(R.plurals.goal_history_count, count, count));
        view.recordsRows.removeAllViews();
        for (int i = 0; i < count; i++) {
            GoalDto.Contribution item = model.contributions.get(i);
            var row = ItemContributionBinding.inflate(LayoutInflater.from(this), view.recordsRows, false);
            String date = GoalListActivity.readableDate(item.date());
            String text = getString(R.string.goal_contribution_row, date, GoalRules.plain(item.amount()));
            row.contributionDivider.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            row.recordName.setText(date);
            row.recordAmount.setText(Money.rupees(this, item.amount()));
            row.contributionRow.setContentDescription(text);
            row.recordRemove.setEnabled(!busy);
            row.recordRemove.setContentDescription(getString(R.string.records_remove_named, text));
            row.recordRemove.setOnClickListener(ignored -> AccountListActivity.confirm(this, model.work,
                    R.string.goal_contribution_remove, () -> model.deleteContribution(item.id())));
            view.recordsRows.addView(row.getRoot());
        }
    }
}
