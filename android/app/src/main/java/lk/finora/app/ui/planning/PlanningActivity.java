package lk.finora.app.ui.planning;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.PlanningRepository;
import lk.finora.app.databinding.ActivityPlanningBinding;
import lk.finora.app.databinding.ItemPlanningCutBinding;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.transactions.TransactionRows;

/**
 * T64.3/D65/D68 Planning, one screen in two modes. Daily spending guide: Rs. inputs, next income date from the
 * calendar, tracked-bills card with confirmation, allowance card. Goal simulation: goal, monthly savings now,
 * More/Less change, category cuts, estimate card. Result cards keep the earlier full sentences as their spoken text.
 */
public final class PlanningActivity extends SecureActivity {
    private static final String SIMULATION = "simulation";
    private PlanningViewModel model;
    private ActivityPlanningBinding view;
    private boolean simulator;
    private boolean choicesRendered;
    private boolean restoring;
    private PlanningRepository.Guide shownConfirmation;
    private final List<View> inputs = new ArrayList<>();
    private final List<CutRow> cuts = new ArrayList<>();
    private List<GoalDto> goals = Collections.emptyList();
    private List<CategoryDto> categories = Collections.emptyList();

    public static Intent simulation(Context context) {
        return new Intent(context, PlanningActivity.class).putExtra(SIMULATION, true);
    }

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
        model = new ViewModelProvider(this).get(PlanningViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        restoring = true;
        simulator = getIntent().getBooleanExtra(SIMULATION, false);
        view = ActivityPlanningBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        view.planningTitle.setText(simulator ? R.string.planning_simulator : R.string.planning_guide);
        ViewCompat.setAccessibilityHeading(view.planningTitle, true);
        view.planningInfo.setOnClickListener(clicked -> showSecure(new AlertDialog.Builder(this)
                .setTitle(R.string.planning_how)
                .setMessage(simulator ? getString(R.string.planning_simulation_intro)
                        : getString(R.string.planning_guide_intro) + "\n\n" + getString(R.string.planning_guide_assumptions))
                .setPositiveButton(android.R.string.ok, null).create()));
        if (simulator) setupSimulation(); else setupGuide();
        view.planningCalculate.setOnClickListener(clicked -> { if (simulator) simulate(); else quote(); });
        view.planningRetry.setOnClickListener(clicked -> model.retry(simulator));
        restoring = false;
        if (!simulator) FinancialDatePicker.restore(this, this::selectIncomeDate);
        model.work.changes.observe(this, ignored -> render());
        if (simulator) model.loadChoices();
    }

    @Override protected void onResume() {
        super.onResume();
        if (model != null) AccountListActivity.verify(this, model.work);
    }

    private void setupGuide() {
        view.planningGuideInputs.setVisibility(View.VISIBLE);
        for (EditText field : List.of(view.planningFunds, view.planningSavings, view.planningArrears)) watch(field);
        inputs.add(view.planningIncomeDate);
        inputs.add(view.planningIncomeClear);
        if (model.guideDraft != null) {
            view.planningFunds.setText(model.guideDraft.funds());
            view.planningSavings.setText(model.guideDraft.savings());
            view.planningArrears.setText(model.guideDraft.arrears());
        }
        view.planningIncomeDate.setOnClickListener(clicked -> {
            if (!ready()) return;
            FinancialDatePicker.show(this, R.string.planning_income_date,
                    PlanningRules.pickerStart(model.incomeDate, PlanningRules.today()), this::selectIncomeDate);
        });
        view.planningIncomeClear.setOnClickListener(clicked -> {
            if (!ready()) return;
            model.incomeDate = null;
            showIncomeDate();
            invalidate();
        });
        view.planningConfirm.setOnClickListener(clicked -> confirmQuote());
        showIncomeDate();
    }

    private void selectIncomeDate(LocalDate date) {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        model.incomeDate = date.toString();
        showIncomeDate();
        invalidate();
    }

    private void showIncomeDate() {
        LocalDate date = model.incomeDate == null ? null : LocalDate.parse(model.incomeDate);
        String label = date == null ? getString(R.string.transaction_choose_date) : TransactionRows.fullDate(this, date);
        view.planningIncomeDate.setText(label);
        view.planningIncomeDate.setContentDescription(getString(R.string.planning_income_date) + ": " + label);
        view.planningIncomeClear.setVisibility(date == null ? View.GONE : View.VISIBLE);
    }

    private void setupSimulation() {
        view.planningSimulationInputs.setVisibility(View.VISIBLE);
        watch(view.planningBaseline);
        watch(view.planningAdjustment);
        spinner(view.planningGoal);
        inputs.add(view.planningMore);
        inputs.add(view.planningLess);
        view.planningDirection.addOnButtonCheckedListener((group, id, checked) -> { if (checked) invalidate(); });
        view.planningAddCut.setOnClickListener(clicked -> {
            if (ready() && !categories.isEmpty() && cuts.size() < 20) {
                addCut(0, "");
                invalidate();
            }
        });
        if (model.simulationDraft != null) {
            view.planningBaseline.setText(model.simulationDraft.baseline());
            view.planningAdjustment.setText(model.simulationDraft.adjustment());
            view.planningDirection.check(model.simulationDraft.less() ? R.id.planning_less : R.id.planning_more);
        }
    }

    private boolean less() { return view.planningDirection.getCheckedButtonId() == R.id.planning_less; }

    private void quote() {
        if (!ready()) return;
        try {
            model.quote(new PlanningRepository.GuideInput(amount(view.planningFunds, true, false),
                    amount(view.planningSavings, false, false), model.incomeDate, null,
                    amount(view.planningArrears, false, false)));
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
        return PlanningRules.needsConfirmation(model.guide.status());
    }

    private void simulate() {
        if (!ready() || !model.choicesLoaded) return;
        int idx = view.planningGoal.getSelectedItemPosition();
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
            var change = PlanningRules.monthlyChange(amount(view.planningAdjustment, false, false), less());
            model.simulate(new PlanningRepository.SimulationInput(goals.get(idx).id(),
                    amount(view.planningBaseline, false, true), change, Collections.unmodifiableList(requested)));
        } catch (InvalidInput ignored) { /* Field reports its own error. */ }
    }

    private boolean ready() { return AccountListActivity.verify(this, model.work) && !model.work.busy(); }

    private void addCut(int category, String value) {
        var row = ItemPlanningCutBinding.inflate(getLayoutInflater(), view.planningCuts, false);
        view.planningCuts.addView(row.getRoot());
        spinner(row.cutCategory);
        options(row.cutCategory, categories.stream().map(this::categoryName).collect(Collectors.toList()), category);
        row.cutAmount.setText(value);
        watch(row.cutAmount);
        inputs.add(row.cutRemove);
        var cut = new CutRow(row, row.cutCategory, row.cutAmount);
        cuts.add(cut);
        dividers();
        row.cutRemove.setOnClickListener(clicked -> {
            if (!ready()) return;
            view.planningCuts.removeView(row.getRoot());
            cuts.remove(cut);
            inputs.removeAll(List.of(row.cutCategory, row.cutAmount, row.cutRemove));
            dividers();
            invalidate();
        });
    }

    private void dividers() {
        for (int i = 0; i < cuts.size(); i++) cuts.get(i).row().cutDivider.setVisibility(i == 0 ? View.GONE : View.VISIBLE);
    }

    private String categoryName(CategoryDto category) {
        var locale = getResources().getConfiguration().getLocales().get(0);
        return "si".equals(locale.getLanguage()) && category.nameSi() != null ? category.nameSi() : category.name();
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        int status = busy ? R.string.records_loading : model.work.error;
        int message;
        if (simulator) {
            if (model.choicesLoaded && !choicesRendered) {
                choicesRendered = true;
                restoring = true;
                goals = model.goals.stream().filter(g -> !"CANCELLED".equals(g.status())).collect(Collectors.toList());
                categories = model.categories.stream().filter(c -> "EXPENSE".equals(c.kind())).collect(Collectors.toList());
                var draft = model.simulationDraft;
                options(view.planningGoal, goals.stream().map(GoalDto::name).collect(Collectors.toList()),
                        draft == null ? 0 : draft.goal());
                if (draft != null && !categories.isEmpty()) {
                    for (var row : draft.cuts()) addCut(row.category(), row.amount());
                }
                restoring = false;
            }
            message = renderSimulation(busy ? null : model.simulation);
            if (message == 0 && status == 0 && model.choicesLoaded && goals.isEmpty()) message = R.string.planning_no_goals;
            view.planningCalculate.setEnabled(!busy && !goals.isEmpty());
            view.planningAddCut.setEnabled(!busy && !goals.isEmpty() && !categories.isEmpty() && cuts.size() < 20);
        } else {
            message = renderGuide(busy ? null : model.guide);
            view.planningCalculate.setEnabled(!busy);
            view.planningConfirm.setEnabled(!busy && canConfirm());
        }
        // Rotation can restore cut rows above while a calculation is still in flight.
        for (View input : inputs) input.setEnabled(!busy);
        view.planningRetry.setVisibility(!busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        view.planningStatus.setVisibility(status == 0 ? View.GONE : View.VISIBLE);
        view.planningStatus.setText(status == 0 ? "" : getString(status));
        view.planningMessage.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        view.planningMessage.setText(message == 0 ? "" : getString(message));
    }

    /** Shows the guide result and the tracked-bills card; returns the status message to show (0 = none). */
    private int renderGuide(PlanningRepository.Guide guide) {
        view.planningBillsCard.setVisibility(View.GONE);
        view.planningResultCard.setVisibility(View.GONE);
        if (guide == null) return 0;
        var input = model.guideInput;
        if (guide.trackedUnpaidBills() != null) {
            boolean pending = canConfirm();
            int count = guide.trackedUnpaidBillCount() == null ? 0 : guide.trackedUnpaidBillCount();
            String title = getString(pending ? R.string.planning_bills_title : R.string.planning_bills_confirmed);
            view.planningBillsTitle.setText(title);
            view.planningBillsDetail.setText(getResources().getQuantityString(R.plurals.planning_bills_detail, count,
                    count, Money.rupees(this, guide.trackedUnpaidBills())));
            view.planningBillsIcon.setImageResource(pending ? R.drawable.finora_ic_alert : R.drawable.finora_ic_check);
            view.planningBillsIcon.setBackgroundTintList(ColorStateList.valueOf(getColor(
                    pending ? R.color.finora_warn_fill : R.color.finora_success_surface)));
            view.planningBillsIcon.setImageTintList(ColorStateList.valueOf(getColor(
                    pending ? R.color.finora_warn_ink : R.color.finora_green)));
            view.planningBillsSummary.setContentDescription(getString(R.string.planning_tracked,
                    guide.trackedUnpaidBills().toPlainString(), count) + ". " + title);
            view.planningConfirm.setVisibility(pending ? View.VISIBLE : View.GONE);
            view.planningBillsCard.setVisibility(View.VISIBLE);
            if (pending && shownConfirmation != guide) {
                // Confirming is the next step: bring the button above the pinned Calculate bar once per answer.
                shownConfirmation = guide;
                view.planningScroll.post(() -> view.planningScroll.smoothScrollTo(0,
                        view.planningGuideInputs.getTop() + view.planningBillsCard.getBottom()
                                - view.planningScroll.getHeight() + view.planningScroll.getPaddingBottom()));
            }
        }
        if (guide.dailyAllowance() != null && guide.spendingDays() != null && input != null) {
            int days = (int) Math.min(Integer.MAX_VALUE, guide.spendingDays());
            view.planningResultLabel.setText(R.string.planning_can_spend);
            view.planningResultAmount.setText(Money.rupees(this, guide.dailyAllowance()));
            view.planningResultUnit.setText(R.string.planning_per_day);
            view.planningResultUnit.setVisibility(View.VISIBLE);
            String sub = getResources().getQuantityString(R.plurals.planning_for_days, days, days);
            if (guide.nextIncomeDate() != null) {
                sub += " · " + getString(R.string.planning_until,
                        TransactionRows.fullDate(this, LocalDate.parse(guide.nextIncomeDate())));
            }
            view.planningResultSub.setText(sub);
            view.planningResultSub.setVisibility(View.VISIBLE);
            view.planningResultLines.removeAllViews();
            line(R.string.planning_funds, input.availableFunds(), false);
            line(R.string.planning_savings, input.plannedSavings(), true);
            if (guide.trackedUnpaidBills() != null) {
                line(getString(R.string.planning_line_tracked, guide.trackedUnpaidBillCount() == null ? 0
                        : guide.trackedUnpaidBillCount()), guide.trackedUnpaidBills(), true);
            }
            line(R.string.planning_arrears, input.additionalUnpaidBills(), true);
            if (guide.shortfall() != null && guide.shortfall().signum() > 0) {
                line(R.string.planning_shortfall_label, guide.shortfall(), false);
            }
            String note = getString(R.string.planning_rounded);
            if (guide.nextIncomeDate() != null && !"USER_SELECTED".equals(guide.incomeDateSource())) {
                note += "\n" + getString(R.string.planning_date_inferred, guide.nextIncomeDate());
            }
            view.planningResultNote.setText(note);
            view.planningResultCard.setContentDescription(guideSentence(guide));
            view.planningResultCard.setVisibility(View.VISIBLE);
        }
        return PlanningRules.guideMessage(guide.status(), guide.warning());
    }

    /** The earlier full result text, still spoken by TalkBack. */
    private String guideSentence(PlanningRepository.Guide guide) {
        var lines = new ArrayList<String>();
        lines.add(getString(R.string.planning_estimate));
        if (guide.nextIncomeDate() != null) lines.add(getString(
                "USER_SELECTED".equals(guide.incomeDateSource()) ? R.string.planning_date_entered : R.string.planning_date_inferred,
                guide.nextIncomeDate()));
        if (guide.trackedUnpaidBills() != null) lines.add(getString(R.string.planning_tracked,
                guide.trackedUnpaidBills().toPlainString(), guide.trackedUnpaidBillCount()));
        lines.add(getString(R.string.planning_allowance, guide.dailyAllowance().toPlainString(), guide.spendingDays()));
        if (guide.shortfall() != null && guide.shortfall().signum() > 0) lines.add(
                getString(R.string.planning_shortfall, guide.shortfall().toPlainString()));
        if (guide.warning()) lines.add(getString(R.string.planning_zero));
        return String.join(". ", lines);
    }

    /** Shows the simulation estimate card; returns the status message to show (0 = none). */
    private int renderSimulation(PlanningRepository.Simulation simulation) {
        if (simulation == null) {
            view.planningResultCard.setVisibility(View.GONE);
            return 0;
        }
        boolean possible = "POSSIBLE".equals(simulation.status()) && simulation.hypotheticalFinishDate() != null
                && simulation.monthsRequired() != null;
        view.planningResultLines.removeAllViews();
        if (possible) {
            int months = simulation.monthsRequired();
            view.planningResultLabel.setText(R.string.planning_finish_label);
            view.planningResultAmount.setText(PlanningRules.monthYear(simulation.hypotheticalFinishDate(), locale()));
            view.planningResultSub.setText(getResources().getQuantityString(R.plurals.planning_in_months, months, months));
            view.planningResultSub.setVisibility(View.VISIBLE);
            line(R.string.planning_saved_each_month, simulation.hypotheticalMonthlySavings(), false);
        } else {
            view.planningResultLabel.setText(R.string.planning_saved_each_month);
            view.planningResultAmount.setText(Money.rupees(this, simulation.hypotheticalMonthlySavings()));
            view.planningResultSub.setVisibility(View.GONE);
        }
        view.planningResultUnit.setVisibility(View.GONE);
        line(R.string.planning_balance_left, simulation.remaining(), false);
        view.planningResultNote.setText(getString(R.string.planning_estimate_only,
                PlanningRules.monthYear(simulation.spendingReferenceMonth(), locale())));

        var lines = new ArrayList<String>();
        int message = PlanningRules.simulationMessage(simulation.status());
        lines.add(getString(message == 0 ? R.string.planning_estimate : message));
        lines.add(getString(R.string.planning_monthly, simulation.hypotheticalMonthlySavings().toPlainString()));
        lines.add(getString(R.string.planning_remaining, simulation.remaining().toPlainString()));
        if (possible) lines.add(getString(R.string.planning_finish, simulation.hypotheticalFinishDate(),
                simulation.monthsRequired()));
        lines.add(getString(R.string.planning_reference, simulation.spendingReferenceMonth()));
        view.planningResultCard.setContentDescription(String.join(". ", lines));
        view.planningResultCard.setVisibility(View.VISIBLE);
        return message;
    }

    private void line(int label, BigDecimal value, boolean minus) { line(getString(label), value, minus); }

    /** One label and amount row inside the result card; deductions get a minus sign in words and figures. */
    private void line(String label, BigDecimal value, boolean minus) {
        if (value == null) return;
        var row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(4 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, pad);
        var name = new TextView(this);
        name.setText(label);
        name.setTextColor(getColor(R.color.finora_hero_muted));
        name.setTextSize(13);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        var amount = new TextView(this);
        String figure = Money.rupees(this, value);
        amount.setText(minus && value.signum() > 0 ? getString(R.string.planning_minus, figure) : figure);
        amount.setTextColor(getColor(R.color.finora_hero_ink));
        amount.setTextSize(14);
        amount.setTypeface(amount.getTypeface(), android.graphics.Typeface.BOLD);
        amount.setPaddingRelative(pad * 2, 0, 0, 0);
        row.addView(amount, new LinearLayout.LayoutParams(-2, -2));
        view.planningResultLines.addView(row);
    }

    private void spinner(Spinner spinner) {
        inputs.add(spinner);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selection,
                    int position, long id) {
                var old = (Integer) parent.getTag();
                parent.setTag(position);
                if (old != null && old != position) invalidate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
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

    private void watch(EditText field) {
        inputs.add(field);
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { invalidate(); }
            @Override public void afterTextChanged(android.text.Editable text) {}
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (view != null && model.work.isCurrent()) {
            if (simulator) {
                var rows = new ArrayList<PlanningViewModel.CutDraft>();
                if (choicesRendered) {
                    for (var row : cuts) {
                        rows.add(new PlanningViewModel.CutDraft(row.category().getSelectedItemPosition(),
                                row.amount().getText().toString()));
                    }
                }
                model.simulationDraft = new PlanningViewModel.SimulationDraft(
                        choicesRendered ? view.planningGoal.getSelectedItemPosition() : 0,
                        view.planningBaseline.getText().toString(), view.planningAdjustment.getText().toString(),
                        less(), rows);
            } else {
                model.guideDraft = new PlanningViewModel.GuideDraft(view.planningFunds.getText().toString(),
                        view.planningSavings.getText().toString(), view.planningArrears.getText().toString());
            }
        }
        super.onSaveInstanceState(state);
    }

    private void invalidate() {
        if (view == null || restoring || model.work.busy() || !model.work.isCurrent()) return;
        model.invalidateResult(simulator);
        render();
    }

    private BigDecimal amount(EditText field, boolean signed, boolean required) throws InvalidInput {
        String text = field.getText().toString().trim();
        char separator = java.text.DecimalFormatSymbols.getInstance(locale()).getDecimalSeparator();
        if (separator != '.') text = text.replace(separator, '.');
        if (text.isEmpty() && !required) return null;
        if (text.length() <= 17 && text.matches((signed ? "[-+]?" : "") + "[0-9]{1,13}(\\.[0-9]{1,2})?")) {
            return new BigDecimal(text);
        }
        field.setError(getString(R.string.planning_invalid_amount));
        throw new InvalidInput();
    }

    private record CutRow(ItemPlanningCutBinding row, Spinner category, EditText amount) {}
    private static final class InvalidInput extends Exception {}
}
