package lk.finora.app.ui.goals;

import android.os.Bundle;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityGoalFormBinding;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.transactions.TransactionRows;

/**
 * T64.2/D65 add or edit a goal: name, target amount and an optional target date after today (T19.3, D16). D68: the
 * date comes from the calendar behind a No deadline / By a date toggle, with a monthly hint.
 */
public final class GoalFormActivity extends SecureActivity {
    private GoalFormViewModel model;
    private ActivityGoalFormBinding view;
    private boolean filled;

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityGoalFormBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.goalFormHeading, true);
        boolean editing = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0) != 0;
        view.goalFormHeading.setText(editing ? R.string.goal_edit : R.string.goal_add_goal);
        view.goalTargetDate.setOnClickListener(ignored -> pickDate());
        FinancialDatePicker.restore(this, this::selectDate);
        AccountFormActivity.watch(view.formName, text -> model.name = text);
        AccountFormActivity.watch(view.goalTarget, text -> {
            model.target = text;
            showHint();
        });
        view.goalDeadline.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || !filled) return;
            model.hasDeadline = id == R.id.goal_deadline_date;
            view.goalTargetDateError.setVisibility(View.GONE);
            showDeadline();
            if (model.hasDeadline && model.targetDate.isEmpty()) pickDate();
        });
        view.recordsSave.setOnClickListener(ignored -> save());
        view.recordsRetry.setOnClickListener(ignored -> {
            if (model.work.ambiguousCreate) {
                AccountFormActivity.reconcile(this, model.work, GoalListActivity.class);
            } else if (!model.ready) {
                model.load(model.id);
            } else {
                save();
            }
        });
        model.work.changes.observe(this, ignored -> render());
        if (!model.ready && !model.work.busy()) {
            model.load(getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.ready && !filled) {
            view.formName.setText(model.name);
            view.goalTarget.setText(model.target);
            view.goalDeadline.check(model.hasDeadline ? R.id.goal_deadline_date : R.id.goal_deadline_none);
            filled = true;
            showDeadline();
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        view.recordsStatus.setVisibility(view.recordsStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        boolean retry = !model.work.busy() && (model.work.error != 0 || model.work.ambiguousCreate);
        view.recordsRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        view.formName.setEnabled(enabled);
        view.goalTarget.setEnabled(enabled);
        view.goalDeadline.setEnabled(enabled);
        view.goalDeadlineNone.setEnabled(enabled);
        view.goalDeadlineDate.setEnabled(enabled);
        view.goalTargetDate.setEnabled(enabled);
    }

    /** Date button text, the date group's visibility and the monthly hint. */
    private void showDeadline() {
        view.goalDateGroup.setVisibility(model.hasDeadline ? View.VISIBLE : View.GONE);
        LocalDate date = GoalRules.parseDate(model.targetDate);
        String label = date == null ? getString(R.string.transaction_choose_date) : TransactionRows.fullDate(this, date);
        view.goalTargetDate.setText(label);
        view.goalTargetDate.setContentDescription(getString(R.string.goal_date_spoken,
                getString(R.string.goal_target_date), label));
        showHint();
    }

    /** "About Rs. X a month to reach this by <date>", only with a valid amount and a date at least a month away. */
    private void showHint() {
        if (view == null) return;
        LocalDate date = model.hasDeadline ? GoalRules.parseDate(model.targetDate) : null;
        BigDecimal need = null;
        if (date != null) {
            try {
                BigDecimal target = GoalRules.parseAmount(model.target);
                need = GoalRules.monthlyNeed(target.subtract(model.savedSoFar), GoalRules.today(), date);
            } catch (IllegalArgumentException ignored) {
                need = null;
            }
        }
        view.goalMonthlyHint.setVisibility(need == null ? View.GONE : View.VISIBLE);
        if (need != null) {
            view.goalMonthlyHint.setText(getString(R.string.goal_monthly_hint, Money.rupees(this, need),
                    TransactionRows.fullDate(this, date)));
        }
    }

    private boolean canSelectDate() {
        return AccountListActivity.verify(this, model.work) && model.ready && !model.work.busy();
    }

    private void pickDate() {
        if (!canSelectDate()) return;
        var current = GoalRules.parseDate(model.targetDate);
        FinancialDatePicker.show(this, R.string.goal_target_date,
                current == null ? GoalRules.today().plusDays(1) : current, this::selectDate);
    }

    private void selectDate(LocalDate chosen) {
        if (!canSelectDate()) return;
        model.targetDate = chosen.toString();
        model.hasDeadline = true;
        view.goalTargetDateError.setVisibility(View.GONE);
        if (view.goalDeadline.getCheckedButtonId() != R.id.goal_deadline_date) {
            view.goalDeadline.check(R.id.goal_deadline_date);
        }
        showDeadline();
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String goalName = view.formName.getText().toString().trim();
        if (!GoalFormViewModel.validName(goalName)) {
            FormFields.error(view.formName, R.string.records_name_invalid);
            return;
        }
        BigDecimal amount;
        try {
            amount = GoalRules.parseAmount(view.goalTarget.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(view.goalTarget, R.string.budget_limit_invalid);
            return;
        }
        String date = model.hasDeadline ? model.targetDate : "";
        if (model.hasDeadline && (date.isEmpty() || !GoalRules.validTargetDate(date, GoalRules.today()))) {
            view.goalTargetDateError.setText(R.string.goal_target_date_invalid);
            view.goalTargetDateError.setVisibility(View.VISIBLE);
            view.goalTargetDate.requestFocus();
            return;
        }
        model.save(goalName, amount, date);
    }
}
