package lk.finora.app.ui.budgets;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.databinding.ActivityBudgetListBinding;
import lk.finora.app.databinding.ItemBudgetBinding;
import lk.finora.app.ui.CategoryIcons;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** T63.2/D65 monthly budgets: left-to-spend summary and progress cards, most urgent first (T17.3). */
public final class BudgetListActivity extends SecureActivity {
    static final String MONTH = "budget_month";
    static final String TAKEN = "budget_taken_categories";
    private BudgetListViewModel model;
    private ActivityBudgetListBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BudgetListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityBudgetListBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.budgetTitle, true);
        view.recordsAdd.setOnClickListener(ignored -> open(0));
        view.recordsRetry.setOnClickListener(ignored -> model.load());
        view.budgetPrevious.setOnClickListener(ignored -> model.shiftMonth(-1));
        view.budgetNext.setOnClickListener(ignored -> model.shiftMonth(1));
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
        view.budgetMonth.setText(monthLabel(model.month));
        view.budgetPrevious.setEnabled(!model.work.busy());
        view.budgetNext.setEnabled(!model.work.busy());
        renderSummary();
        view.recordsRows.removeAllViews();
        for (BudgetDto item : BudgetListViewModel.ordered(model.rows)) view.recordsRows.addView(row(item));
    }

    private void renderSummary() {
        view.budgetSummaryCard.setVisibility(model.rows.isEmpty() ? View.GONE : View.VISIBLE);
        if (model.rows.isEmpty()) return;
        var totals = BudgetListViewModel.totals(model.rows);
        boolean over = totals.left().signum() < 0;
        String label = getString(over ? R.string.budget_summary_over : R.string.budget_summary_left);
        String amount = Money.rupees(this, totals.left().abs());
        String used = getString(R.string.budget_summary_used, Money.rupees(this, totals.used()),
                Money.rupees(this, totals.limit()));
        view.budgetSummaryLabel.setText(label);
        view.budgetSummaryAmount.setText(amount);
        view.budgetSummaryUsed.setText(used);
        view.budgetSummaryBar.setIndicatorColor(getColor(over ? R.color.finora_error_surface : R.color.finora_mint));
        view.budgetSummaryBar.setProgressCompat(BudgetListViewModel.percent(totals.used(), totals.limit()), false);
        view.budgetSummaryCard.setContentDescription(label + ", " + amount + ". " + used);
    }

    private View row(BudgetDto item) {
        var card = ItemBudgetBinding.inflate(LayoutInflater.from(this), view.recordsRows, false);
        CategoryDto category = model.categoryById.get(item.categoryId());
        String name = categoryName(category);
        String state = item.state();
        int ink = getColor(BudgetListViewModel.stateColor(state));
        int percent = BudgetListViewModel.percent(item.used(), item.limitAmount());

        card.recordName.setText(name);
        card.recordDetails.setText(getString(R.string.budget_used_of, Money.rupees(this, item.used()),
                Money.rupees(this, item.limitAmount())));
        card.budgetIcon.setImageResource(CategoryIcons.forCategory(category == null ? null : category.name(), false));
        card.budgetIcon.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.finora_error_surface)));
        card.budgetIcon.setImageTintList(ColorStateList.valueOf(getColor(R.color.finora_error)));

        card.budgetState.setText(BudgetListViewModel.stateChip(state));
        card.budgetState.setTextColor(ink);
        card.budgetState.setBackgroundTintList(ColorStateList.valueOf(getColor(BudgetListViewModel.stateFill(state))));
        card.budgetState.setCompoundDrawablesRelativeWithIntrinsicBounds(BudgetListViewModel.stateIcon(state), 0, 0, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(card.budgetState, ColorStateList.valueOf(ink));

        card.budgetProgress.setIndicatorColor(getColor(BudgetListViewModel.stateBar(state)));
        card.budgetProgress.setTrackStopIndicatorSize(0);
        card.budgetProgress.setIndicatorTrackGapSize(0);
        card.budgetProgress.setProgressCompat(percent, false);

        boolean over = item.remaining().signum() < 0;
        String left = over ? getString(R.string.budget_over_by_short, Money.rupees(this, item.remaining().negate()))
                : "NEAR".equals(state) ? getString(R.string.budget_left_percent, Money.rupees(this, item.remaining()),
                        percentText(percent))
                : getString(R.string.budget_left_short, Money.rupees(this, item.remaining()));
        card.budgetLeft.setText(left);
        boolean urgent = over || "NEAR".equals(state);
        card.budgetLeft.setTextColor(urgent ? ink : getColor(R.color.finora_muted));
        card.budgetLeft.setTypeface(null, urgent ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (over) card.budgetCard.setStrokeColor(ink);

        // TalkBack: one sentence for the card, the state as its state, and edit/remove as actions.
        String details = over
                ? getString(R.string.budget_over_by, plain(item.used()), plain(item.limitAmount()),
                        plain(item.remaining().negate()))
                : getString(R.string.budget_left, plain(item.used()), plain(item.limitAmount()),
                        plain(item.remaining()));
        card.budgetCard.setContentDescription(name + ". " + details);
        ViewCompat.setStateDescription(card.budgetCard, stateSentence(state));
        card.budgetCard.setOnClickListener(ignored -> open(item.id()));
        ViewCompat.replaceAccessibilityAction(card.budgetCard,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.records_edit_named, name), null);
        Runnable remove = () -> AccountListActivity.confirm(this, model.work,
                R.string.budget_remove_message, () -> model.delete(item.id()));
        card.budgetCard.setOnLongClickListener(ignored -> {
            remove.run();
            return true;
        });
        ViewCompat.addAccessibilityAction(card.budgetCard, getString(R.string.records_remove_named, name),
                (target, arguments) -> {
                    remove.run();
                    return true;
                });
        return card.getRoot();
    }

    private String stateSentence(String state) {
        int label = BudgetListViewModel.stateLabel(state);
        return label == R.string.budget_state_near ? getString(label, percentText(80)) : getString(label);
    }

    private String percentText(int percent) {
        var locale = getResources().getConfiguration().getLocales().get(0);
        return NumberFormat.getPercentInstance(locale).format(BigDecimal.valueOf(percent).movePointLeft(2));
    }

    private void open(long id) {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        long[] taken = new long[model.rows.size()];
        for (int i = 0; i < taken.length; i++) taken[i] = model.rows.get(i).categoryId();
        startActivity(new Intent(this, BudgetFormActivity.class)
                .putExtra(AccountListActivity.ITEM_ID, id)
                .putExtra(MONTH, model.month.toString())
                .putExtra(TAKEN, taken));
    }

    private String categoryName(CategoryDto category) {
        if (category == null) return getString(R.string.budget_unknown_category);
        String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
        return "si".equals(language) && category.nameSi() != null && !category.nameSi().trim().isEmpty()
                ? category.nameSi() : category.name();
    }

    /** Full month name and year in the app language, e.g. "November 2026" (also used by bills). */
    public static String monthLabel(java.time.YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()));
    }

    private static String plain(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
