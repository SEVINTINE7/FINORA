package lk.finora.app.ui.goals;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Savings goals with progress bars (T19.3). Tapping a goal opens its contributions. */
public final class GoalListActivity extends SecureActivity {
    private GoalListViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(GoalListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_goal_list);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.goal_heading), true);
        findViewById(R.id.records_add).setOnClickListener(view -> {
            if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
                startActivity(new Intent(this, GoalFormActivity.class));
            }
        });
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        for (GoalDto goal : model.rows) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_goal, rows, false);
            ((TextView) row.findViewById(R.id.record_name)).setText(goal.name());
            show(this, goal, row.findViewById(R.id.goal_progress), row.findViewById(R.id.record_details),
                    row.findViewById(R.id.goal_status));
            View open = row.findViewById(R.id.goal_open);
            open.setContentDescription(getString(R.string.goal_open_named, goal.name()));
            open.setOnClickListener(view -> {
                if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
                    startActivity(new Intent(this, GoalDetailActivity.class)
                            .putExtra(AccountListActivity.ITEM_ID, goal.id()));
                }
            });
            rows.addView(row);
        }
    }

    /** Progress bar, "Saved X of Y · Z left · by <date>" and the coloured status. Shared with the detail screen. */
    static void show(Context context, GoalDto goal, ProgressBar progress, TextView details, TextView status) {
        int color = context.getColor(GoalRules.statusColor(goal.status()));
        progress.setProgress(goal.progressPercent());
        progress.setProgressTintList(ColorStateList.valueOf(color));
        String text = context.getString(R.string.goal_progress_text, GoalRules.plain(goal.saved()),
                GoalRules.plain(goal.targetAmount()), GoalRules.plain(goal.remaining()), goal.progressPercent());
        if (goal.targetDate() != null) {
            text = text + " · " + context.getString(R.string.goal_by_date, readableDate(goal.targetDate()));
        }
        details.setText(text);
        progress.setContentDescription(goal.name() + ". " + text);
        status.setText(GoalRules.statusLabel(goal.status()));
        status.setTextColor(color);
    }

    static String readableDate(String isoDate) {
        return LocalDate.parse(isoDate)
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
    }
}
