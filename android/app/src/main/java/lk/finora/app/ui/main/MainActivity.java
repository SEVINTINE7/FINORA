package lk.finora.app.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import lk.finora.app.R;
import lk.finora.app.ui.FinancialDatePicker;
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
import lk.finora.app.ui.insights.InsightsActivity;
import lk.finora.app.ui.planning.PlanningActivity;
import lk.finora.app.ui.settings.SettingsActivity;
import lk.finora.app.ui.transactions.TransactionFormActivity;
import lk.finora.app.ui.transactions.TransactionListPanel;
import lk.finora.app.ui.transactions.TransactionViewModel;

public final class MainActivity extends ProtectedHomeActivity {
    private static final String DESTINATION = "destination";
    private static final int[] LABELS = {R.string.home_tab, R.string.records_tab,
            R.string.plan_tab, R.string.insights_tab, R.string.more_tab};
    private static final int[] ICONS = {R.drawable.finora_nav_home, R.drawable.finora_nav_records,
            R.drawable.finora_nav_plan, R.drawable.finora_nav_insights, R.drawable.finora_nav_more};
    private int destination;
    private DashboardViewModel dashboard;
    private TransactionListPanel records;
    private BottomNavigationView navigation;
    private View language;
    private Button logout;
    private boolean resumedOnce;
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
        LinearLayout planActions = new LinearLayout(this);
        planActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(planActions, R.string.budget_list_title, BudgetListActivity.class);
        addMoreAction(planActions, R.string.bill_list_title, BillListActivity.class);
        addMoreAction(planActions, R.string.goal_list_title, GoalListActivity.class);
        addMoreAction(planActions, R.string.planning_guide, PlanningActivity.class);
        Button simulation = new Button(this);
        simulation.setText(R.string.planning_simulator);
        simulation.setOnClickListener(view -> startActivity(PlanningActivity.simulation(this)));
        planActions.addView(simulation, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout insightActions = new LinearLayout(this);
        insightActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(insightActions, R.string.insight_open, InsightsActivity.class);
        addFocusableRoute(insightActions, R.string.advice_title, AdviceActivity.class);

        // T34.5: alert channel + push token (no-op until Firebase is configured, D57)
        lk.finora.app.notifications.AlertNotifications.createChannel(this);
        lk.finora.app.notifications.PushRegistration.register(getApplicationContext());
        int margin = getResources().getDimensionPixelSize(R.dimen.finora_screen_margin);
        title.setPadding(margin, margin, margin, 0);
        ((LinearLayout.LayoutParams) title.getLayoutParams()).gravity = Gravity.START;
        dashboard = new ViewModelProvider(this).get(DashboardViewModel.class);
        DashboardPanel homePanel = new DashboardPanel(this, dashboard);
        View home = homePanel.view();
        records = new TransactionListPanel(this, new ViewModelProvider(this).get(TransactionViewModel.class),
                false, () -> {});
        View recordsView = records.view();
        ScrollView actions = new ScrollView(this);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setPadding(margin, margin / 2, margin, margin);
        actions.addView(tabs);
        for (LinearLayout section : new LinearLayout[]{moreActions, planActions, insightActions}) {
            tabs.addView(section);
        }
        if (language != null) moreActions.addView(language, new LinearLayout.LayoutParams(-1, -2));
        if (logout != null) moreActions.addView(logout, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout stack = new FrameLayout(this);
        stack.addView(home, new FrameLayout.LayoutParams(-1, -1));
        stack.addView(recordsView, new FrameLayout.LayoutParams(-1, -1));
        stack.addView(actions, new FrameLayout.LayoutParams(-1, -1));
        ExtendedFloatingActionButton addTransaction = new ExtendedFloatingActionButton(this);
        addTransaction.setId(R.id.shell_add_transaction);
        addTransaction.setText(R.string.shell_add);
        addTransaction.setIconResource(R.drawable.finora_ic_add);
        addTransaction.setContentDescription(getString(R.string.transaction_open_manual));
        addTransaction.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.finora_green)));
        addTransaction.setTextColor(getColor(R.color.finora_action_ink));
        addTransaction.setIconTint(ColorStateList.valueOf(getColor(R.color.finora_action_ink)));
        addTransaction.setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        actionParams.setMargins(margin, margin, margin, margin);
        stack.addView(addTransaction, actionParams);
        layout.addView(stack, 1, new LinearLayout.LayoutParams(-1, 0, 1));

        navigation = new BottomNavigationView(this);
        homePanel.onOpenRecords(() -> navigation.setSelectedItemId(LABELS[1]));
        navigation.setId(R.id.shell_navigation);
        navigation.setBackgroundColor(getColor(R.color.finora_nav_surface));
        navigation.setElevation(getResources().getDisplayMetrics().density * 8);
        navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.setItemHorizontalTranslationEnabled(false);
        navigation.setLabelFontScalingEnabled(true);
        navigation.setLabelMaxLines(2);
        navigation.setItemActiveIndicatorEnabled(true);
        navigation.setItemActiveIndicatorColor(ColorStateList.valueOf(getColor(R.color.finora_mint)));
        int[][] states = {{android.R.attr.state_checked}, {}};
        navigation.setItemIconTintList(new ColorStateList(states,
                new int[]{getColor(R.color.finora_navy), getColor(R.color.finora_muted)}));
        navigation.setItemTextColor(new ColorStateList(states,
                new int[]{getColor(R.color.finora_ink), getColor(R.color.finora_muted)}));
        for (int index = 0; index < LABELS.length; index++) {
            int label = LABELS[index];
            navigation.getMenu().add(0, label, Menu.NONE, label).setIcon(ICONS[index])
                    .setContentDescription(getString(label));
        }
        navigation.setOnItemSelectedListener(item -> {
            int selected = 0;
            while (LABELS[selected] != item.getItemId()) selected++;
            int former = destination;
            destination = selected;
            int tab = LABELS[destination];
            boolean panel = destination == 0 || tab == R.string.records_tab;
            home.setVisibility(destination == 0 ? View.VISIBLE : View.GONE);
            recordsView.setVisibility(tab == R.string.records_tab ? View.VISIBLE : View.GONE);
            actions.setVisibility(panel ? View.GONE : View.VISIBLE);
            title.setVisibility(panel ? View.GONE : View.VISIBLE);
            title.setText(destination == 0 ? titleResource() : tab);
            moreActions.setVisibility(tab == R.string.more_tab ? View.VISIBLE : View.GONE);
            planActions.setVisibility(tab == R.string.plan_tab ? View.VISIBLE : View.GONE);
            insightActions.setVisibility(tab == R.string.insights_tab ? View.VISIBLE : View.GONE);
            addTransaction.setVisibility(panel ? View.VISIBLE : View.GONE);
            if (destination == 0 && former > 0) dashboard.refresh();
            if (tab == R.string.records_tab && former != destination) records.show();
            return true;
        });
        int restored = state == null ? 0 : state.getInt(DESTINATION);
        if (restored < 0 || restored >= LABELS.length) restored = 0;
        // Run the listener for the restored tab even when it is already the menu's selected item.
        destination = -1;
        navigation.setSelectedItemId(LABELS[restored]);
        if (destination == -1) destination = restored;
        layout.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override protected boolean lightShell() { return true; }

    @Override protected void placeAccountControls(LinearLayout layout, View language, Button logout) {
        // Language and sign-out live in the More tab (T62).
        this.language = language;
        this.logout = logout;
    }

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (dashboard != null && destination == 0) dashboard.refresh();
        // Rows added from the form or edited elsewhere appear when returning to the Records tab.
        if (records != null && destination == 1 && resumedOnce) records.show();
        resumedOnce = true;
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
