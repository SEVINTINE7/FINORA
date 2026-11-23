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
import android.widget.TextView;

import lk.finora.app.R;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.auth.ProtectedHomeActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;
import lk.finora.app.ui.categories.CategoryListActivity;
import lk.finora.app.ui.transactions.TransactionActivity;

public final class MainActivity extends ProtectedHomeActivity {
    private static final String DESTINATION = "destination";
    private static final int[] LABELS = {R.string.home_tab, R.string.records_tab,
            R.string.plan_tab, R.string.insights_tab, R.string.more_tab};
    private int destination;

    @Override protected String requiredRole() { return "USER"; }
    @Override protected int titleResource() { return R.string.home_user; }

    @Override
    protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {
        LinearLayout moreActions = new LinearLayout(this);
        moreActions.setOrientation(LinearLayout.VERTICAL);
        addMoreAction(moreActions, R.string.account_list_title, AccountListActivity.class);
        addMoreAction(moreActions, R.string.category_list_title, CategoryListActivity.class);
        layout.addView(moreActions, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout recordActions = new LinearLayout(this);
        addMoreAction(recordActions, R.string.transaction_title, TransactionActivity.class);
        layout.addView(recordActions, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout planActions = new LinearLayout(this);
        addMoreAction(planActions, R.string.budget_list_title, BudgetListActivity.class);
        layout.addView(planActions, new LinearLayout.LayoutParams(-1, -2));

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
            destination = group.indexOfChild(group.findViewById(checkedId));
            title.setText(destination == 0 ? titleResource() : LABELS[destination]);
            moreActions.setVisibility(LABELS[destination] == R.string.more_tab ? View.VISIBLE : View.GONE);
            recordActions.setVisibility(LABELS[destination] == R.string.records_tab ? View.VISIBLE : View.GONE);
            planActions.setVisibility(LABELS[destination] == R.string.plan_tab ? View.VISIBLE : View.GONE);
        });
        int restored = state == null ? 0 : state.getInt(DESTINATION);
        navigation.check(navigation.getChildAt(restored).getId());
        layout.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addMoreAction(LinearLayout actions, int label, Class<?> screen) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> startActivity(new Intent(this, screen)));
        actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(DESTINATION, destination);
        super.onSaveInstanceState(state);
    }
}
