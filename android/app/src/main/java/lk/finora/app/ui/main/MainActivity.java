package lk.finora.app.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.advice.AdviceActivity;
import lk.finora.app.ui.auth.ProtectedHomeActivity;
import lk.finora.app.ui.bills.BillListActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;
import lk.finora.app.ui.categories.CategoryListActivity;
import lk.finora.app.ui.dashboard.DashboardPanel;
import lk.finora.app.ui.dashboard.DashboardViewModel;
import lk.finora.app.ui.goals.GoalListActivity;
import lk.finora.app.ui.help.FaqActivity;
import lk.finora.app.ui.history.HistoryActivity;
import lk.finora.app.ui.importer.CsvImportActivity;
import lk.finora.app.ui.insights.InsightsActivity;
import lk.finora.app.ui.settings.SettingsActivity;
import lk.finora.app.ui.sms.SmsAccessActivity;
import lk.finora.app.ui.transactions.TransactionActivity;
import lk.finora.app.ui.transactions.TransactionFormActivity;

public final class MainActivity extends ProtectedHomeActivity {
    private static final String DESTINATION = "destination";
    private static final int[] LABELS = {R.string.home_tab, R.string.records_tab,
            R.string.plan_tab, R.string.insights_tab, R.string.more_tab};
    private int destination;
    private DashboardViewModel dashboard;
    @Override protected String requiredRole() { return "USER"; }
    @Override protected int titleResource() { return R.string.home_user; }
    @Override protected boolean showPlaceholder() { return false; }

    @Override
    protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {
        LinearLayout moreActions = new LinearLayout(this);
        moreActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(moreActions, R.string.account_list_title, AccountListActivity.class);
        addMoreAction(moreActions, R.string.category_list_title, CategoryListActivity.class);
        addMoreAction(moreActions, R.string.settings_title, SettingsActivity.class);
        addFocusableRoute(moreActions, R.string.history_title, HistoryActivity.class);
        addFocusableRoute(moreActions, R.string.finance_help, FaqActivity.class);
        addFocusableRoute(moreActions, R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class);
        LinearLayout recordActions = new LinearLayout(this);
        recordActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(recordActions, R.string.transaction_title, TransactionActivity.class);
        addMoreAction(recordActions, R.string.transaction_open_manual, TransactionFormActivity.class);
        addMoreAction(recordActions, R.string.sms_csv_import, CsvImportActivity.class);
        addMoreAction(recordActions, R.string.sms_access_title, SmsAccessActivity.class);
        LinearLayout planActions = new LinearLayout(this);
        planActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(planActions, R.string.budget_list_title, BudgetListActivity.class);
        addMoreAction(planActions, R.string.bill_list_title, BillListActivity.class);
        addMoreAction(planActions, R.string.goal_list_title, GoalListActivity.class);
        LinearLayout insightActions = new LinearLayout(this);
        insightActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(insightActions, R.string.insight_open, InsightsActivity.class);
        addFocusableRoute(insightActions, R.string.advice_title, AdviceActivity.class);

        dashboard = new ViewModelProvider(this).get(DashboardViewModel.class);
        ScrollView home = new DashboardPanel(this, dashboard).view();
        layout.addView(home, 1, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView actions = new ScrollView(this);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        actions.addView(tabs);
        for (LinearLayout section : new LinearLayout[]{moreActions, recordActions, planActions, insightActions}) {
            tabs.addView(section);
        }
        layout.addView(actions, 2, new LinearLayout.LayoutParams(-1, 0, 1));

        RadioGroup navigation = new RadioGroup(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        int[][] states = {{android.R.attr.state_checked}, {}};
        ColorStateList colors = new ColorStateList(states,
                new int[]{getColor(R.color.finora_mint), getColor(R.color.finora_on_navy)});
        int touchHeight = Math.round(48 * getResources().getDisplayMetrics().density);
        for (int label : LABELS) {
            RadioButton tab = new RadioButton(this);
            tab.setId(View.generateViewId());
            tab.setText(label);
            tab.setTextColor(colors);
            tab.setButtonDrawable(null);
            tab.setPadding(0, 0, 0, 0);
            tab.setSingleLine(false);
            tab.setHorizontallyScrolling(false);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(touchHeight);
            navigation.addView(tab, new RadioGroup.LayoutParams(0, -1, 1));
        }
        navigation.setOnCheckedChangeListener((group, checkedId) -> {
            int selected = group.indexOfChild(group.findViewById(checkedId));
            if (selected < 0) return;
            int former = destination;
            destination = selected;
            home.setVisibility(destination == 0 ? View.VISIBLE : View.GONE);
            actions.setVisibility(destination == 0 ? View.GONE : View.VISIBLE);
            title.setText(destination == 0 ? titleResource() : LABELS[destination]);
            moreActions.setVisibility(LABELS[destination] == R.string.more_tab ? View.VISIBLE : View.GONE);
            recordActions.setVisibility(LABELS[destination] == R.string.records_tab ? View.VISIBLE : View.GONE);
            planActions.setVisibility(LABELS[destination] == R.string.plan_tab ? View.VISIBLE : View.GONE);
            insightActions.setVisibility(LABELS[destination] == R.string.insights_tab ? View.VISIBLE : View.GONE);
            if (destination == 0 && former != 0) dashboard.refresh();
        });
        int restored = state == null ? 0 : state.getInt(DESTINATION);
        if (restored < 0 || restored >= LABELS.length) restored = 0;
        navigation.check(navigation.getChildAt(restored).getId());
        layout.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override protected void onResume() {
        super.onResume();
        if (dashboard != null && destination == 0) dashboard.refresh();
    }

    private void addMoreAction(LinearLayout actions, int label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> startActivity(new Intent(this, screen)));
        actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addFocusableRoute(LinearLayout actions, int label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        bindFocusReturningRoute(button, label, screen);
        actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(DESTINATION, destination);
        super.onSaveInstanceState(state);
    }
}
