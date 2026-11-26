package lk.finora.app.ui.planning;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.PlanningRepository;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class PlanningActivity extends SecureActivity {
    private static final String SIMULATION = "simulation";
    private PlanningViewModel model;
    private boolean simulator;
    private LinearLayout form;
    private TextView status;
    private TextView result;
    private Button calculate;
    private Button confirm;
    private Button addCut;
    private Button retry;
    private boolean choicesRendered;
    private boolean restoring;
    private Spinner goalPicker;
    private EditText funds, savings, date, arrears, baseline, adjustment;
    private final List<View> inputs = new ArrayList<>();
    private final List<CutRow> cuts = new ArrayList<>();
    private List<GoalDto> goals = java.util.Collections.emptyList();
    private List<CategoryDto> categories = java.util.Collections.emptyList();

    public static Intent simulation(Context context) {
        return new Intent(context, PlanningActivity.class).putExtra(SIMULATION, true);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(PlanningViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        restoring = true;
        simulator = getIntent().getBooleanExtra(SIMULATION, false);
        var scroll = new ScrollView(this);
        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        scroll.addView(form);
        setContentView(scroll);
        var title = label(simulator ? R.string.planning_simulator : R.string.planning_guide);
        title.setTextSize(24);
        ViewCompat.setAccessibilityHeading(title, true);
        if (simulator) setupSimulation(); else setupGuide();
        status = label(0);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        result = label(0);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        calculate = button(R.string.planning_calculate, simulator ? this::simulate : this::quote);
        if (!simulator) confirm = button(R.string.planning_confirm_bills, this::confirmQuote);
        retry = button(R.string.records_retry, () -> model.retry(simulator));
        retry.setVisibility(View.GONE);
        restoring = false;
        model.work.changes.observe(this, ignored -> render());
        if (simulator) model.loadChoices();
    }

    @Override protected void onResume() {
        super.onResume();
        if (model != null) AccountListActivity.verify(this, model.work);
    }

    private void setupGuide() {
        label(R.string.planning_guide_intro);
        funds = money(R.string.planning_funds, true, form);
        savings = money(R.string.planning_savings, false, form);
        arrears = money(R.string.planning_arrears, false, form);
        date = input(R.string.planning_income_date, InputType.TYPE_CLASS_TEXT, form);
        if (model.guideDraft != null) {
            funds.setText(model.guideDraft.funds());
            savings.setText(model.guideDraft.savings());
            arrears.setText(model.guideDraft.arrears());
            date.setText(model.guideDraft.date());
        }
        button(R.string.planning_assumptions, () -> showSecure(new AlertDialog.Builder(this)
                .setTitle(R.string.planning_assumptions).setMessage(R.string.planning_guide_assumptions)
                .setPositiveButton(android.R.string.ok, null).create()));
    }

    private void setupSimulation() {
        label(R.string.planning_simulation_intro);
        goalPicker = spinner(form, R.string.planning_goal);
        baseline = money(R.string.planning_baseline, false, form);
        adjustment = money(R.string.planning_adjustment, true, form);
        addCut = button(R.string.planning_add_cut, () -> {
            if (ready() && !categories.isEmpty() && cuts.size() < 20) {
                addCut(0, "");
                invalidate();
            }
        });
        if (model.simulationDraft != null) {
            baseline.setText(model.simulationDraft.baseline());
            adjustment.setText(model.simulationDraft.adjustment());
        }
    }

    private void quote() {
        if (!ready()) return;
        try {
            String next = date.getText().toString().trim();
            if (!next.isEmpty()) {
                try { next = LocalDate.parse(next).toString(); }
                catch (java.time.DateTimeException invalid) {
                    date.setError(getString(R.string.planning_invalid_date));
                    return;
                }
            }
            model.quote(new PlanningRepository.GuideInput(amount(funds, true, false), amount(savings, false, false),
                    next.isEmpty() ? null : next, null, amount(arrears, false, false)));
        } catch (InvalidInput ignored) { /* Field reports its own error. */ }
    }

    private void confirmQuote() {
        if (!ready() || !canConfirm()) return;
        var input = model.guideInput;
        var tracked = model.guide.trackedUnpaidBills();
        showSecure(new AlertDialog.Builder(this).setTitle(R.string.planning_confirm_bills)
                .setMessage(getString(R.string.planning_confirm_message, tracked.toPlainString()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.planning_confirm_bills, (dialog, which) -> {
                    if (ready() && model.guideInput == input && canConfirm()) model.quote(input.confirm(tracked));
                }).create());
    }

    private boolean canConfirm() {
        if (model.guide == null || model.guideInput == null || model.guide.trackedUnpaidBills() == null) return false;
        return "BILL_CONFIRMATION_REQUIRED".equals(model.guide.status()) || "BILLS_CHANGED".equals(model.guide.status());
    }

    private void simulate() {
        if (!ready()) return;
        if (!model.choicesLoaded) return;
        int idx = goalPicker.getSelectedItemPosition();
        if (idx < 0 || idx >= goals.size()) return;
        try {
            List<PlanningRepository.CategoryCut> requested = new ArrayList<>();
            var seen = new HashSet<Long>();
            for (var row : cuts) {
                int selected = row.category().getSelectedItemPosition();
                if (selected < 0 || selected >= categories.size()) return;
                long categoryId = categories.get(selected).id();
                var reduction = amount(row.amount(), false, true);
                if (reduction.signum() == 0 || !seen.add(categoryId)) {
                    row.amount().setError(getString(R.string.planning_invalid_cut));
                    return;
                }
                requested.add(new PlanningRepository.CategoryCut(categoryId, reduction));
            }
            model.simulate(new PlanningRepository.SimulationInput(goals.get(idx).id(),
                    amount(baseline, false, true), amount(adjustment, true, true), java.util.Collections.unmodifiableList(requested)));
        } catch (InvalidInput ignored) { /* Field reports its own error. */ }
    }

    private boolean ready() { return AccountListActivity.verify(this, model.work) && !model.work.busy(); }

    private void addCut(int category, String value) {
        var row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        form.addView(row, form.indexOfChild(addCut));
        var picker = spinner(row, R.string.planning_category);
        options(picker, categories.stream().map(this::categoryName).collect(java.util.stream.Collectors.toList()), category);
        var amount = money(R.string.planning_reduction, false, row);
        amount.setText(value);
        var remove = new Button(this);
        remove.setText(R.string.planning_remove_cut);
        row.addView(remove);
        inputs.add(remove);
        var cut = new CutRow(picker, amount);
        cuts.add(cut);
        remove.setOnClickListener(view -> {
            if (!ready()) return;
            form.removeView(row);
            cuts.remove(cut);
            inputs.removeAll(java.util.Arrays.asList(picker, amount, remove));
            invalidate();
        });
    }

    private String categoryName(CategoryDto category) {
        var locale = getResources().getConfiguration().getLocales().get(0);
        return "si".equals(locale.getLanguage()) && category.nameSi() != null ? category.nameSi() : category.name();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        for (View view : inputs) view.setEnabled(!busy);
        int message = busy ? R.string.records_loading : model.work.error;
        result.setText("");
        if (simulator) {
            if (model.choicesLoaded && !choicesRendered) {
                choicesRendered = true;
                restoring = true;
                goals = model.goals.stream().filter(g -> !"CANCELLED".equals(g.status()))
                        .collect(java.util.stream.Collectors.toList());
                categories = model.categories.stream().filter(c -> "EXPENSE".equals(c.kind()))
                        .collect(java.util.stream.Collectors.toList());
                var draft = model.simulationDraft;
                options(goalPicker, goals.stream().map(GoalDto::name).collect(java.util.stream.Collectors.toList()),
                        draft == null ? 0 : draft.goal());
                if (draft != null && !categories.isEmpty()) {
                    for (var row : draft.cuts()) addCut(row.category(), row.amount());
                }
                restoring = false;
            }
            if (message == 0 && model.choicesLoaded && goals.isEmpty()) message = R.string.planning_no_goals;
            calculate.setEnabled(!busy && !goals.isEmpty());
            addCut.setEnabled(!busy && !goals.isEmpty() && !categories.isEmpty() && cuts.size() < 20);
            if (!busy && model.simulation != null) renderSimulation(model.simulation);
        } else {
            calculate.setEnabled(!busy);
            confirm.setEnabled(!busy && canConfirm());
            if (!busy && model.guide != null) renderGuide(model.guide);
        }
        retry.setVisibility(!busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        status.setText(message == 0 ? "" : getString(message));
    }

    private void renderGuide(PlanningRepository.Guide guide) {
        var lines = new ArrayList<String>();
        lines.add(getString(guideMessage(guide.status())));
        if (guide.nextIncomeDate() != null) lines.add(getString(
                "USER_SELECTED".equals(guide.incomeDateSource()) ? R.string.planning_date_entered : R.string.planning_date_inferred,
                guide.nextIncomeDate()));
        if (guide.trackedUnpaidBills() != null) lines.add(getString(R.string.planning_tracked,
                guide.trackedUnpaidBills().toPlainString(), guide.trackedUnpaidBillCount()));
        if (guide.dailyAllowance() != null && guide.spendingDays() != null) lines.add(getString(R.string.planning_allowance,
                guide.dailyAllowance().toPlainString(), guide.spendingDays()));
        if (guide.shortfall() != null && guide.shortfall().signum() > 0) lines.add(
                getString(R.string.planning_shortfall, guide.shortfall().toPlainString()));
        if (guide.warning() && "READY".equals(guide.status())) lines.add(getString(R.string.planning_zero));
        result.setText(String.join("\n", lines));
    }

    private void renderSimulation(PlanningRepository.Simulation simulation) {
        int message = switch (simulation.status()) {
            case "POSSIBLE" -> R.string.planning_estimate;
            case "ALREADY_COMPLETE" -> R.string.planning_completed;
            case "NO_MONTHLY_CAPACITY" -> R.string.planning_no_capacity;
            case "BEYOND_HORIZON" -> R.string.planning_horizon;
            default -> R.string.records_failed;
        };
        var lines = new ArrayList<String>();
        lines.add(getString(message));
        lines.add(getString(R.string.planning_monthly, simulation.hypotheticalMonthlySavings().toPlainString()));
        lines.add(getString(R.string.planning_remaining, simulation.remaining().toPlainString()));
        if ("POSSIBLE".equals(simulation.status()) && simulation.hypotheticalFinishDate() != null
                && simulation.monthsRequired() != null) {
            lines.add(getString(R.string.planning_finish, simulation.hypotheticalFinishDate(), simulation.monthsRequired()));
        }
        lines.add(getString(R.string.planning_reference, simulation.spendingReferenceMonth()));
        result.setText(String.join("\n", lines));
    }

    private static int guideMessage(String status) {
        return switch (status) {
            case "READY" -> R.string.planning_estimate;
            case "NO_SPENDABLE_FUNDS" -> R.string.planning_zero;
            case "SHORTFALL" -> R.string.planning_overcommitted;
            case "MISSING_BALANCE" -> R.string.planning_funds;
            case "MISSING_PLANNED_SAVINGS" -> R.string.planning_savings;
            case "MISSING_NEXT_INCOME_DATE" -> R.string.planning_date_missing;
            case "INCOME_TODAY" -> R.string.planning_income_today;
            case "INCOME_DATE_PASSED" -> R.string.planning_income_passed;
            case "BILL_CONFIRMATION_REQUIRED" -> R.string.planning_confirm_bills;
            case "BILLS_CHANGED" -> R.string.planning_bills_changed;
            case "MISSING_ADDITIONAL_BILLS" -> R.string.planning_arrears;
            default -> R.string.records_failed;
        };
    }

    private TextView label(int text) {
        var view = new TextView(this);
        if (text != 0) view.setText(text);
        form.addView(view);
        return view;
    }

    private Button button(int text, Runnable action) {
        var view = new Button(this);
        view.setText(text);
        view.setOnClickListener(clicked -> action.run());
        form.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Spinner spinner(LinearLayout into, int description) {
        var view = new Spinner(this);
        view.setContentDescription(getString(description));
        view.setMinimumHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        into.addView(view, new LinearLayout.LayoutParams(-1, -2));
        inputs.add(view);
        view.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selection,
                    int position, long id) {
                var old = (Integer) parent.getTag();
                parent.setTag(position);
                if (old != null && old != position) invalidate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        return view;
    }

    private void options(Spinner spinner, List<String> values, int selected) {
        var adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (!values.isEmpty()) {
            int index = Math.max(0, Math.min(selected, values.size() - 1));
            spinner.setTag(index);
            spinner.setSelection(index);
        }
    }

    private EditText money(int hint, boolean signed, LinearLayout into) {
        return input(hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | (signed ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0), into);
    }

    private EditText input(int hint, int keyboard, LinearLayout into) {
        var field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine();
        field.setInputType(keyboard);
        into.addView(field, new LinearLayout.LayoutParams(-1, -2));
        inputs.add(field);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                invalidate();
            }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
        return field;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (result != null && model.work.isCurrent()) {
            if (simulator) {
                if (choicesRendered) {
                    var rows = new ArrayList<PlanningViewModel.CutDraft>();
                    for (var row : cuts) {
                        rows.add(new PlanningViewModel.CutDraft(row.category().getSelectedItemPosition(),
                                row.amount().getText().toString()));
                    }
                    model.simulationDraft = new PlanningViewModel.SimulationDraft(
                            goalPicker.getSelectedItemPosition(), baseline.getText().toString(),
                            adjustment.getText().toString(), rows);
                } else {
                    model.simulationDraft = new PlanningViewModel.SimulationDraft(0, baseline.getText().toString(),
                            adjustment.getText().toString(), java.util.Collections.emptyList());
                }
            } else {
                model.guideDraft = new PlanningViewModel.GuideDraft(funds.getText().toString(),
                        savings.getText().toString(), arrears.getText().toString(), date.getText().toString());
            }
        }
        super.onSaveInstanceState(state);
    }

    private void invalidate() {
        if (result == null || restoring || model.work.busy() || !model.work.isCurrent()) return;
        model.invalidateResult();
        render();
    }

    private BigDecimal amount(EditText field, boolean signed, boolean required) throws InvalidInput {
        String text = field.getText().toString().trim();
        char separator = java.text.DecimalFormatSymbols.getInstance(
                getResources().getConfiguration().getLocales().get(0)).getDecimalSeparator();
        if (separator != '.') text = text.replace(separator, '.');
        if (text.isEmpty() && !required) return null;
        if (text.length() <= 17 && text.matches((signed ? "[-+]?" : "") + "[0-9]{1,13}(\\.[0-9]{1,2})?")) {
            return new BigDecimal(text);
        }
        field.setError(getString(R.string.planning_invalid_amount));
        throw new InvalidInput();
    }

    private record CutRow(Spinner category, EditText amount) {}
    private static final class InvalidInput extends Exception {}
}
