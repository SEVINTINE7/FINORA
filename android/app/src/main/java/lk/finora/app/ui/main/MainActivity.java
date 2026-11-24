package lk.finora.app.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final String RETURN_FOCUS = "returnFocus";
    private static final String ROUTE_RETURNED = "routeReturned";
    private static final long TALKBACK_WINDOW_SETTLE_MS = 500;
    private static final int[] LABELS = {R.string.home_tab, R.string.records_tab,
            R.string.plan_tab, R.string.insights_tab, R.string.more_tab};
    private int destination;
    private int returnFocus;
    private boolean routeReturned;
    private DashboardViewModel dashboard;
    private Button historyAction;
    private Button adviceAction;
    private Runnable pendingFocusRestore;
    private final ActivityResultLauncher<Intent> historyOrAdvice =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                routeReturned = true;
                restoreRouteFocus();
            });

    @Override protected String requiredRole() { return "USER"; }
    @Override protected int titleResource() { return R.string.home_user; }
    @Override protected boolean showPlaceholder() { return false; }

    @Override
    protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {
        if (state != null) {
            returnFocus = state.getInt(RETURN_FOCUS);
            routeReturned = state.getBoolean(ROUTE_RETURNED);
        }
        LinearLayout moreActions = new LinearLayout(this);
        moreActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(moreActions, R.string.account_list_title, AccountListActivity.class);
        addMoreAction(moreActions, R.string.category_list_title, CategoryListActivity.class);
        addMoreAction(moreActions, R.string.settings_title, SettingsActivity.class);
        historyAction = addFocusableRoute(moreActions, R.string.history_title, HistoryActivity.class);
        addMoreAction(moreActions, R.string.finance_help, FaqActivity.class);
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
        adviceAction = addFocusableRoute(insightActions, R.string.advice_title, AdviceActivity.class);

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

    private Button addFocusableRoute(LinearLayout actions, int label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> {
            returnFocus = label;
            historyOrAdvice.launch(new Intent(this, screen));
        });
        actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) restoreRouteFocus();
        else cancelRouteFocus();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) cancelRouteFocus();
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) cancelRouteFocus();
        return super.dispatchKeyEvent(event);
    }

    private void cancelRouteFocus() {
        if (pendingFocusRestore != null) {
            getWindow().getDecorView().removeCallbacks(pendingFocusRestore);
            pendingFocusRestore = null;
        }
    }

    // Restore the button the user came from after TalkBack processes the returned window.
    // An intervening touch, key or window change cancels the restore.
    private void restoreRouteFocus() {
        if (!routeReturned || !hasWindowFocus() || isFinishing()) return;
        Button route = returnFocus == R.string.history_title ? historyAction
                : returnFocus == R.string.advice_title ? adviceAction : null;
        routeReturned = false;
        returnFocus = 0;
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        if (route != null && route.isShown() && accessibility != null && accessibility.isTouchExplorationEnabled()) {
            pendingFocusRestore = () -> {
                pendingFocusRestore = null;
                if (!hasWindowFocus() || !route.isShown()) return;
                route.requestRectangleOnScreen(new Rect(0, 0, route.getWidth(), route.getHeight()), true);
                route.setFocusableInTouchMode(true);
                route.requestFocus();
            };
            getWindow().getDecorView().postDelayed(pendingFocusRestore, TALKBACK_WINDOW_SETTLE_MS);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(DESTINATION, destination);
        state.putInt(RETURN_FOCUS, returnFocus);
        state.putBoolean(ROUTE_RETURNED, routeReturned);
        super.onSaveInstanceState(state);
    }
}
