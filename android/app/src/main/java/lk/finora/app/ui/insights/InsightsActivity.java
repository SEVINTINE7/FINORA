package lk.finora.app.ui.insights;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;

import lk.finora.app.R;
import lk.finora.app.data.repo.InsightsRepository.Declaration;
import lk.finora.app.data.repo.InsightsRepository.Insight;
import lk.finora.app.data.repo.InsightsRepository.Part;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.insights.InsightsViewModel.DeclarationDraft;

public final class InsightsActivity extends AppCompatActivity {
    private InsightsViewModel model;
    private LinearLayout results;
    private TextView month;
    private Button previous;
    private Button next;
    private AlertDialog editor;
    private Supplier<DeclarationDraft> currentDraft;
    private boolean restoreEditor;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(InsightsViewModel.class);
        if (!AccountListActivity.verify(this, model.patternsWork)) return;
        restoreEditor = model.rotatedDraft != null;
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.finora_surface));
        var body = new LinearLayout(this);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);
        line(body, getString(R.string.insight_open), true);
        var navigation = new LinearLayout(this);
        previous = button(R.string.dashboard_previous, () -> model.shift(-1));
        next = button(R.string.dashboard_next, () -> model.shift(1));
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(navigation);
        month = line(body, "", true);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results);
        for (Work work : new Work[]{model.patternsWork, model.scoreWork, model.declarationWork}) {
            work.changes.observe(this, ignored -> render());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.patternsWork)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.patternsWork)) {
            if (editor != null) editor.dismiss();
            if (results != null) results.removeAllViews();
            return;
        }
        results.removeAllViews();
        month.setText(model.month.format(DateTimeFormatter.ofPattern("LLLL yyyy",
                getResources().getConfiguration().getLocales().get(0))));
        previous.setEnabled(!model.busy() && model.month.getYear() >= 1000
                && model.month.minusMonths(1).getYear() >= 1000);
        next.setEnabled(!model.busy() && model.month.plusMonths(1).getYear() <= 9999);
        if (model.patterns != null) {
            if (model.patterns.length == 0) line(results, getString(R.string.insight_empty), false);
            for (Insight row : model.patterns) showInsight(row);
        }
        line(results, getString(R.string.score_heading), true);
        if (model.score != null) {
            line(results, model.score.total() == null ? getString(R.string.score_unavailable)
                    : getString(R.string.score_total, model.score.total().toString()), false);
            for (Part part : model.score.parts()) showPart(part);
        }
        if (model.declaration != null && !model.busy()) {
            results.addView(button(R.string.score_inputs, this::editDeclaration));
        }
        if (model.busy()) line(results, getString(R.string.records_loading), false);
        for (Work work : new Work[]{model.patternsWork, model.scoreWork, model.declarationWork}) {
            if (work.error != 0) {
                line(results, getString(work.error), false);
                results.addView(button(R.string.records_retry, model::load));
                break;
            }
        }
        line(results, getString(R.string.score_disclaimer), false);
        if (restoreEditor && model.declaration != null && !model.busy()) {
            restoreEditor = false;
            editDeclaration();
        }
    }

    private void showInsight(Insight row) {
        Map<String, BigDecimal> facts = row.facts();
        int title;
        String detail;
        switch (row.type()) {
            case "HIGH_WANTS" -> {
                title = R.string.insight_wants;
                detail = share(facts, "wants", "wantsRatio", R.string.insight_income_share);
            }
            case "HIGH_FIXED_BILLS" -> {
                title = R.string.insight_bills;
                detail = share(facts, "fixedBills", "fixedBillsRatio", R.string.insight_income_share);
            }
            case "PAYDAY_SPENDING" -> {
                title = R.string.insight_payday;
                detail = share(facts, "paydaySpend", "paydayShare", R.string.insight_expense_share);
            }
            case "WEEKEND_SPENDING" -> {
                title = R.string.insight_weekend;
                detail = evidence(facts, R.string.insight_weekend_values,
                        "weekendSpend", "weekendDailyAverage", "weekdayDailyAverage");
            }
            default -> { return; }
        }
        line(results, getString(title), true);
        line(results, getString(R.string.insight_period, row.from(), row.to()), false);
        line(results, detail, false);
    }

    private String share(Map<String, BigDecimal> facts, String amount, String ratio, int label) {
        if (facts.get(amount) == null || facts.get(ratio) == null) return getString(R.string.records_failed);
        return getString(label, number(facts.get(amount)), percent(facts.get(ratio)));
    }

    private String evidence(Map<String, BigDecimal> facts, int label, String... keys) {
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            BigDecimal value = facts.get(keys[i]);
            if (value == null) return getString(R.string.records_failed);
            values[i] = number(value);
        }
        return getString(label, values);
    }

    private void showPart(Part part) {
        int label = switch (part.key()) {
            case "SPENDING_CONTROL" -> R.string.score_spending;
            case "DEBT_PAYMENTS" -> R.string.score_debt;
            case "GOAL_PROGRESS" -> R.string.score_goals;
            case "SAVINGS" -> R.string.score_savings;
            default -> R.string.score_emergency;
        };
        line(results, getString(label), true);
        if (part.points() != null) {
            line(results, getString(R.string.score_points, number(part.points()), Integer.toString(part.weight())), false);
        } else {
            line(results, getString("NOT_APPLICABLE".equals(part.status())
                    ? R.string.score_not_applicable : R.string.score_unavailable), false);
        }
        int reason = switch (part.reasonKey()) {
            case "ZERO_INCOME" -> R.string.score_zero_income;
            case "SPENDING_EXCEEDS_INCOME" -> R.string.score_spending_exceeds;
            case "SPENDING_BREAK_EVEN" -> R.string.score_spending_equal;
            case "SPENDING_WITHIN_INCOME" -> R.string.score_spending_within;
            case "SAVINGS_TARGET_MET" -> R.string.score_savings_met;
            case "SAVINGS_BELOW_TARGET" -> R.string.score_savings_below;
            case "DEBT_INPUT_MISSING" -> R.string.score_debt_missing;
            case "NO_APPLICABLE_TRACKED_DEBT" -> R.string.score_debt_na;
            case "DEBT_PAID_MARKER_PROXY" -> R.string.score_debt_basis;
            case "GOAL_INPUT_MISSING" -> R.string.score_goal_missing;
            case "NO_APPLICABLE_GOALS" -> R.string.score_goal_na;
            case "GOAL_RECORDED_PROGRESS" -> R.string.score_goal_basis;
            case "EMERGENCY_DECLARATION_MISSING" -> R.string.score_reserve_missing;
            case "ESSENTIAL_BASELINE_UNAVAILABLE" -> R.string.score_needs_missing;
            case "EMERGENCY_USER_DECLARED" -> R.string.score_reserve_basis;
            default -> R.string.records_failed;
        };
        line(results, getString(reason), false);
        if (part.points() == null) return;
        Map<String, BigDecimal> facts = part.facts();
        String supporting = switch (part.key()) {
            case "SPENDING_CONTROL" -> evidence(facts, R.string.score_fact_spending, "income", "consumption");
            case "DEBT_PAYMENTS", "GOAL_PROGRESS" -> evidence(facts, R.string.score_fact_progress, "credited", "target");
            case "SAVINGS" -> facts.get("supported") == null || facts.get("targetRate") == null
                    ? getString(R.string.records_failed)
                    : getString(R.string.score_fact_savings, number(facts.get("supported")),
                            percent(facts.get("targetRate")));
            default -> evidence(facts, R.string.score_fact_emergency, "reserve", "monthlyNeeds");
        };
        line(results, supporting, false);
    }

    private void editDeclaration() {
        if (!model.patternsWork.isCurrent() || model.declaration == null || model.busy()) return;
        var fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.finora_surface));
        scroll.addView(fields);
        var current = model.declaration;
        DeclarationDraft draft = model.rotatedDraft;
        model.rotatedDraft = null;
        if (draft == null) {
            draft = new DeclarationDraft(current.emergencyReserve() == null ? "" : number(current.emergencyReserve()),
                    current.reserveAsOf(), current.noApplicableDebt(), current.noApplicableGoals());
        }
        var reserve = field(fields, R.string.score_reserve, draft.amount());
        reserve.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        var date = field(fields, R.string.score_reserve_date, draft.date());
        date.setInputType(android.text.InputType.TYPE_CLASS_DATETIME
                | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        var debt = choice(fields, R.string.score_no_debt, draft.noDebt());
        var goals = choice(fields, R.string.score_no_goals, draft.noGoals());
        currentDraft = () -> new DeclarationDraft(reserve.getText().toString(), date.getText().toString(),
                selected(debt), selected(goals));
        editor = new AlertDialog.Builder(this).setTitle(R.string.score_inputs).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.records_save, null).create();
        editor.setOnDismissListener(dialog -> {
            editor = null;
            currentDraft = null;
        });
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                Declaration value = parse(reserve.getText().toString(), date.getText().toString(), model.month,
                        LocalDate.now(ZoneId.of("Asia/Colombo")), selected(debt), selected(goals));
                if (!model.patternsWork.isCurrent() || model.busy()) return;
                model.save(value);
                editor.dismiss();
            } catch (IllegalArgumentException invalid) {
                reserve.setError(getString(R.string.score_input_invalid));
            }
        });
    }

    @Override protected void onDestroy() {
        if (isChangingConfigurations() && editor != null && editor.isShowing()
                && currentDraft != null && model.patternsWork.isCurrent()) {
            model.rotatedDraft = currentDraft.get();
        }
        if (editor != null) editor.dismiss();
        super.onDestroy();
    }

    static Declaration parse(String amount, String date, YearMonth month, LocalDate today,
                             Boolean noDebt, Boolean noGoals) {
        amount = amount.trim();
        date = date.trim();
        if (amount.isEmpty() && date.isEmpty()) return new Declaration(null, null, noDebt, noGoals);
        if (!amount.matches("[0-9]{1,13}([.,][0-9]{1,2})?") || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException();
        }
        try {
            LocalDate parsed = LocalDate.parse(date);
            if (!YearMonth.from(parsed).equals(month) || parsed.isAfter(today)) throw new IllegalArgumentException();
        } catch (java.time.DateTimeException invalid) {
            throw new IllegalArgumentException(invalid);
        }
        return new Declaration(new BigDecimal(amount.replace(',', '.')), date, noDebt, noGoals);
    }

    private RadioGroup choice(LinearLayout target, int label, Boolean value) {
        line(target, getString(label), true);
        var group = new RadioGroup(this);
        for (int labelId : new int[]{R.string.score_unknown, R.string.score_yes, R.string.score_no}) {
            var option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(labelId);
            group.addView(option);
        }
        group.check(group.getChildAt(value == null ? 0 : value ? 1 : 2).getId());
        target.addView(group);
        return group;
    }

    private Boolean selected(RadioGroup group) {
        int index = group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));
        return index < 1 ? null : index == 1;
    }

    private EditText field(LinearLayout target, int label, String value) {
        var edit = new EditText(this);
        edit.setHint(label);
        edit.setText(value);
        target.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    private Button button(int label, Runnable action) {
        var button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView line(LinearLayout target, String text, boolean heading) {
        var view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.finora_ink));
        view.setTextSize(heading ? 20 : 16);
        int padding = Math.round(6 * getResources().getDisplayMetrics().density);
        view.setPadding(0, padding, 0, padding);
        ViewCompat.setAccessibilityHeading(view, heading);
        target.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private String number(BigDecimal value) { return value.toPlainString(); }

    private String percent(BigDecimal value) {
        var format = NumberFormat.getPercentInstance(getResources().getConfiguration().getLocales().get(0));
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
