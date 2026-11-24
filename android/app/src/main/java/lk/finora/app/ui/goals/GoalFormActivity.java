package lk.finora.app.ui.goals;

import android.os.Bundle;
import android.widget.EditText;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Add or edit a goal: name, target amount and an optional target date after today (T19.3, D16). */
public final class GoalFormActivity extends SecureActivity {
    private GoalFormViewModel model;
    private boolean filled;
    private EditText name;
    private EditText target;
    private EditText targetDate;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_goal_form);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.goal_form_heading), true);
        name = findViewById(R.id.form_name);
        target = findViewById(R.id.goal_target);
        targetDate = findViewById(R.id.goal_target_date);
        AccountFormActivity.watch(name, text -> model.name = text);
        AccountFormActivity.watch(target, text -> model.target = text);
        AccountFormActivity.watch(targetDate, text -> model.targetDate = text);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
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
            name.setText(model.name);
            target.setText(model.target);
            targetDate.setText(model.targetDate);
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        name.setEnabled(enabled);
        target.setEnabled(enabled);
        targetDate.setEnabled(enabled);
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String goalName = name.getText().toString().trim();
        if (!GoalFormViewModel.validName(goalName)) {
            name.setError(getString(R.string.records_name_invalid));
            return;
        }
        BigDecimal amount;
        try {
            amount = GoalRules.parseAmount(target.getText().toString());
        } catch (IllegalArgumentException failure) {
            target.setError(getString(R.string.budget_limit_invalid));
            return;
        }
        String date = targetDate.getText().toString();
        if (!GoalRules.validTargetDate(date, GoalRules.today())) {
            targetDate.setError(getString(R.string.goal_target_date_invalid));
            return;
        }
        model.save(goalName, amount, date);
    }
}
