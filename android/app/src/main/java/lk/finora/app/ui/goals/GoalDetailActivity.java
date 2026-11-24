package lk.finora.app.ui.goals;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;

import lk.finora.app.R;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.ui.accounts.AccountListActivity;

/** One goal: progress, add a contribution, contribution history with remove, edit/cancel/resume/delete. */
public final class GoalDetailActivity extends AppCompatActivity {
    private GoalDetailViewModel model;
    private EditText amount;
    private EditText date;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalDetailViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_goal_detail);
        for (int heading : new int[]{R.id.goal_name, R.id.goal_add_heading, R.id.goal_history_heading}) {
            ViewCompat.setAccessibilityHeading(findViewById(heading), true);
        }
        amount = findViewById(R.id.contribution_amount);
        date = findViewById(R.id.contribution_date);
        if (state == null) date.setText(GoalRules.today().toString());
        model.id = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0);

        findViewById(R.id.contribution_add).setOnClickListener(view -> addContribution());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
            // After an unclear save, reloading shows whether the contribution is in the history
            model.work.ambiguousCreate = false;
            model.load(model.id);
        });
        findViewById(R.id.goal_edit).setOnClickListener(view -> {
            if (!model.work.busy()) {
                startActivity(new Intent(this, GoalFormActivity.class)
                        .putExtra(AccountListActivity.ITEM_ID, model.id));
            }
        });
        findViewById(R.id.goal_cancel).setOnClickListener(view -> toggleCancelled());
        findViewById(R.id.goal_delete).setOnClickListener(view -> AccountListActivity.confirm(this, model.work,
                R.string.goal_delete_message, model::delete));
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work) && !model.work.ambiguousCreate) model.load(model.id);
    }

    private void addContribution() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy() || model.goal == null) return;
        BigDecimal value;
        try {
            value = GoalRules.parseAmount(amount.getText().toString());
        } catch (IllegalArgumentException failure) {
            amount.setError(getString(R.string.budget_limit_invalid));
            return;
        }
        String day = date.getText().toString().trim();
        if (!GoalRules.validContributionDate(day, GoalRules.today())) {
            date.setError(getString(R.string.goal_contribution_date_invalid));
            return;
        }
        model.addContribution(value, day);
    }

    private void toggleCancelled() {
        GoalDto goal = model.goal;
        if (goal == null || model.work.busy()) return;
        if (!GoalRules.canEditAsIs(goal.targetDate(), GoalRules.today())) {
            // D16 refuses any edit while the stored target date is not in the future
            new AlertDialog.Builder(this).setMessage(R.string.goal_date_passed)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        boolean cancel = !"CANCELLED".equals(goal.status());
        if (!cancel) {
            model.setCancelled(false);
            return;
        }
        new AlertDialog.Builder(this).setMessage(R.string.goal_cancel_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.goal_cancel, (dialog, which) -> model.setCancelled(true))
                .show();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.deleted) { finish(); return; }
        if (model.contributionAdded) {
            model.contributionAdded = false;
            amount.getText().clear();
        }
        boolean busy = model.work.busy();
        GoalDto goal = model.goal;
        if (goal != null) {
            ((TextView) findViewById(R.id.goal_name)).setText(goal.name());
            GoalListActivity.show(this, goal, findViewById(R.id.goal_progress), findViewById(R.id.goal_details),
                    findViewById(R.id.goal_status));
            ((Button) findViewById(R.id.goal_cancel)).setText("CANCELLED".equals(goal.status())
                    ? R.string.goal_resume : R.string.goal_cancel);
        }
        boolean ready = goal != null && !busy;
        findViewById(R.id.contribution_add).setEnabled(ready && !model.work.ambiguousCreate);
        findViewById(R.id.goal_edit).setEnabled(ready);
        findViewById(R.id.goal_cancel).setEnabled(ready);
        findViewById(R.id.goal_delete).setEnabled(ready);
        findViewById(R.id.records_retry).setEnabled(!busy && model.work.error != 0);
        ((TextView) findViewById(R.id.records_retry)).setText(model.work.ambiguousCreate
                ? R.string.records_reconcile : R.string.records_retry);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error;
        status.setText(message == 0 ? "" : getString(message));

        findViewById(R.id.goal_no_contributions).setVisibility(
                goal != null && model.contributions.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        for (GoalDto.Contribution item : model.contributions) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_contribution, rows, false);
            String text = getString(R.string.goal_contribution_row, GoalListActivity.readableDate(item.date()),
                    GoalRules.plain(item.amount()));
            ((TextView) row.findViewById(R.id.record_name)).setText(text);
            View remove = row.findViewById(R.id.record_remove);
            remove.setEnabled(!busy);
            remove.setContentDescription(getString(R.string.records_remove_named, text));
            remove.setOnClickListener(view -> AccountListActivity.confirm(this, model.work,
                    R.string.goal_contribution_remove, () -> model.deleteContribution(item.id())));
            rows.addView(row);
        }
    }
}
