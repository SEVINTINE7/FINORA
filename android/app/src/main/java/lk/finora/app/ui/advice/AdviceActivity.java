package lk.finora.app.ui.advice;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.finora.app.R;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.data.repo.MonthlyRepository;
import lk.finora.app.data.repo.MonthlyRepository.Advice;
import lk.finora.app.data.repo.MonthlyRepository.Rating;
import lk.finora.app.databinding.ActivityAdviceBinding;
import lk.finora.app.databinding.ItemAdviceBinding;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** T66.2/D65/D70 Advice: banner, goal chips, one card per item with its checked figures, 1–5 rating. */
public final class AdviceActivity extends SecureActivity {
    /** T66.2: the month to open (yyyy-MM), e.g. from Insights; defaults to the current month. */
    public static final String MONTH = "lk.finora.app.ADVICE_MONTH";
    private Model model;
    private ActivityAdviceBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(Model.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        if (!model.opened) {
            model.opened = true;
            String extra = getIntent().getStringExtra(MONTH);
            if (extra != null) {
                YearMonth requested = YearMonth.parse(extra);
                if (!requested.isAfter(YearMonth.now(ZoneId.of("Asia/Colombo")))) model.month = requested;
            }
        }
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityAdviceBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.adviceHeading, true);
        ViewCompat.setAccessibilityHeading(view.adviceBannerTitle, true);
        ViewCompat.setAccessibilityHeading(view.adviceRatingPrompt, true);
        view.adviceSwitch.switchPrevious.setOnClickListener(ignored -> model.shift(-1));
        view.adviceSwitch.switchNext.setOnClickListener(ignored -> model.shift(1));
        view.adviceRetry.setOnClickListener(ignored -> model.load());
        view.adviceSubmit.setOnClickListener(ignored -> model.rate());
        for (int value = 1; value <= 5; value++) {
            var button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(String.valueOf(value));
            button.setTextSize(17);
            button.setMinHeight(dp(52));
            button.setContentDescription(getString(R.string.advice_rating_option, Integer.toString(value)));
            view.adviceRating.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        view.adviceRating.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) {
                if (group.getCheckedButtonId() == View.NO_ID && !model.work.busy()) model.selected = 0;
            } else {
                model.selected = group.indexOfChild(group.findViewById(id)) + 1;
            }
            view.adviceSubmit.setEnabled(model.selected > 0 && !model.work.busy());
            markRating();
        });
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.work) && !model.reconcile) model.load();
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        view.adviceSwitch.switchLabel.setText(model.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale())));
        view.adviceSwitch.getRoot().setVisibility(model.reconcile ? View.GONE : View.VISIBLE);
        view.adviceSwitch.switchPrevious.setEnabled(!busy && model.month.minusMonths(1).getYear() >= 1000);
        view.adviceSwitch.switchNext.setEnabled(!busy
                && model.month.isBefore(YearMonth.now(ZoneId.of("Asia/Colombo"))));
        int message = busy ? R.string.records_loading : model.work.error;
        view.adviceStatus.setText(message == 0 ? "" : getString(message));
        view.adviceStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        view.adviceRetry.setText(model.reconcile ? R.string.advice_check_rating : R.string.records_retry);
        view.adviceRetry.setVisibility(!busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        renderGoalPicker();

        view.adviceItems.removeAllViews();
        boolean ready = !model.reconcile && !busy && model.result != null;
        boolean any = ready && !model.result.items().isEmpty();
        view.adviceEmpty.setVisibility(ready && !any ? View.VISIBLE : View.GONE);
        view.adviceBanner.setVisibility(any ? View.VISIBLE : View.GONE);
        view.adviceRatingCard.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) return;
        boolean verified = model.result.verified();
        view.adviceBannerTitle.setText(verified ? R.string.advice_verified : R.string.advice_rules);
        view.adviceBannerText.setText(verified ? R.string.advice_verified_help : R.string.advice_rules_help);
        view.adviceBannerIcon.setImageResource(verified ? R.drawable.finora_ic_shield : R.drawable.finora_ic_info);
        view.adviceBanner.setCardBackgroundColor(getColor(verified ? R.color.finora_success_surface : R.color.finora_neutral_surface));
        view.adviceBannerTitle.setTextColor(getColor(verified ? R.color.finora_green : R.color.finora_ink));
        view.adviceBannerIcon.setImageTintList(ColorStateList.valueOf(getColor(verified ? R.color.finora_green : R.color.finora_muted)));
        for (var item : model.result.items()) showItem(item);
        renderRating();
    }

    private void showItem(MonthlyRepository.Item item) {
        var card = ItemAdviceBinding.inflate(getLayoutInflater(), view.adviceItems, false);
        int section = AdviceRules.section(item.actionId());
        card.adviceItemTitle.setText(section != 0 ? section : R.string.advice_next_step);
        ViewCompat.setAccessibilityHeading(card.adviceItemTitle, true);
        card.adviceItemText.setText(section != 0 ? item.text() : adviceText(item));
        card.adviceIcon.setImageResource(AdviceRules.icon(item.actionId()));
        int[][] tones = {
                {R.color.finora_success_surface, R.color.finora_green},
                {R.color.finora_warn_fill, R.color.finora_warn_ink},
                {R.color.finora_neutral_surface, R.color.finora_chart_2},
                {R.color.finora_neutral_surface, R.color.finora_muted}};
        int[] tone = tones[AdviceRules.tone(item.actionId())];
        card.adviceIcon.setBackgroundTintList(ColorStateList.valueOf(getColor(tone[0])));
        card.adviceIcon.setImageTintList(ColorStateList.valueOf(getColor(tone[1])));
        var figures = AdviceRules.distinct(item.numbers());
        card.adviceFigures.setVisibility(figures.isEmpty() ? View.GONE : View.VISIBLE);
        for (var fact : figures) card.adviceFigures.addView(figure(fact));
        view.adviceItems.addView(card.getRoot());
    }

    private TextView figure(MonthlyRepository.Fact fact) {
        var label = new TextView(this);
        String value = AdviceRules.plain(fact.value());
        label.setText(switch (fact.unit()) {
            case "PERCENT" -> getString(R.string.advice_unit_percent, value);
            case "MONTHS" -> getString(R.string.advice_unit_months, value);
            default -> Money.rupees(this, fact.value());
        });
        label.setTextSize(13);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(getColor(R.color.finora_ink));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMinHeight(dp(28));
        label.setPadding(dp(8), 0, dp(8), 0);
        var shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(dp(8));
        shape.setColor(getColor(R.color.finora_surface));
        shape.setStroke(dp(1), getColor(R.color.finora_card_line));
        label.setBackground(shape);
        return label;
    }

    private void renderRating() {
        Integer rated = model.result.rating();
        boolean open = rated == null;
        view.adviceRating.setVisibility(open ? View.VISIBLE : View.GONE);
        view.adviceRatingScale.setVisibility(open ? View.VISIBLE : View.GONE);
        view.adviceSubmit.setVisibility(open ? View.VISIBLE : View.GONE);
        view.adviceRated.setVisibility(open ? View.GONE : View.VISIBLE);
        if (!open) {
            view.adviceRated.setText(getString(R.string.advice_rated, rated.toString()));
            return;
        }
        int want = model.selected;
        if (want > 0) view.adviceRating.check(view.adviceRating.getChildAt(want - 1).getId());
        else view.adviceRating.clearChecked();
        model.selected = want;
        for (int i = 0; i < view.adviceRating.getChildCount(); i++) {
            view.adviceRating.getChildAt(i).setEnabled(!model.work.busy());
        }
        view.adviceSubmit.setEnabled(model.selected > 0 && !model.work.busy());
    }

    /** The chosen rating shows a tick as well as its fill, so the choice never relies on colour alone. */
    private void markRating() {
        for (int i = 0; i < view.adviceRating.getChildCount(); i++) {
            var button = (MaterialButton) view.adviceRating.getChildAt(i);
            button.setIconResource(button.isChecked() ? R.drawable.finora_ic_tick : 0);
            button.setIconPadding(dp(2));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        }
    }

    /** D58 coaching sections and their headings; other action ids are the T25 catalogue. */
    static final Map<String, Integer> SECTIONS = Map.of(
            "GOAL_PLAN", R.string.advice_section_goal, "CUT_WANTS", R.string.advice_section_wants,
            "NEEDS_BEHAVIOUR", R.string.advice_section_needs, "SPENDING_PATTERN", R.string.advice_section_pattern,
            "SALARY_PLAN", R.string.advice_section_salary);

    private void renderGoalPicker() {
        boolean show = model.goals != null && !model.goals.isEmpty();
        view.adviceGoalLabel.setVisibility(show ? View.VISIBLE : View.GONE);
        view.adviceGoals.setVisibility(show ? View.VISIBLE : View.GONE);
        view.adviceGoals.removeAllViews();
        if (!show) return;
        for (GoalDto goal : model.goals) goalChip(goal.name(), goal.id());
        goalChip(getString(R.string.advice_goal_none), null);
    }

    private void goalChip(String text, Long id) {
        var chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, view.adviceGoals, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        view.adviceGoals.addView(chip);
        chip.setChecked(java.util.Objects.equals(id, model.goalId));
        chip.setEnabled(!model.work.busy() && !model.reconcile);
        chip.setOnClickListener(ignored -> model.selectGoal(id));
    }

    private String adviceText(MonthlyRepository.Item item) {
        if (!"si".equals(locale().getLanguage())) return item.text();
        return switch (item.actionId()) {
            case "REVIEW_SPENDING_DEFICIT" -> localText(item, R.string.advice_action_deficit, "SPENDING_DEFICIT");
            case "REVIEW_HIGH_WANTS" -> localText(item, R.string.advice_action_wants, "WANTS", "WANTS_REFERENCE");
            case "REVIEW_UNMARKED_LOAN_BILLS" -> localText(item, R.string.advice_action_loan, "UNMARKED_LOAN_BILLS");
            case "BUILD_EMERGENCY_BUFFER" -> localText(item, R.string.advice_action_reserve,
                    "EMERGENCY_RESERVE", "EMERGENCY_NEXT_TARGET");
            case "REVIEW_GOAL_PROGRESS" -> localText(item, R.string.advice_action_goal, "GOAL_REMAINING");
            case "SAVE_WITHIN_AVAILABLE_HEADROOM" -> localText(item, R.string.advice_action_savings, "AFFORDABLE_SAVINGS");
            default -> item.text();
        };
    }

    private String localText(MonthlyRepository.Item item, int label, String... keys) {
        if (item.numbers().size() != keys.length) return item.text();
        Object[] amounts = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            var fact = item.numbers().get(i);
            if (!keys[i].equals(fact.factKey()) || !"LKR".equals(fact.unit())
                    || !model.month.toString().equals(fact.period())) return item.text();
            amounts[i] = fact.value().toPlainString();
        }
        return getString(label, amounts);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public static final class Model extends ViewModel {
        final Work work = new Work(() -> { result = null; selected = 0; reconcile = false; goals = null; goalId = null; });
        private final MonthlyRepository repository = work.requests == null ? null : new MonthlyRepository(work.requests);
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Colombo"));
        private final GoalRepository goalRepository = work.requests == null ? null : new GoalRepository(work.requests);
        Advice result;
        int selected;
        boolean reconcile;
        /** Active goals for the picker (names stay on the phone; only the id is sent). Null until loaded. */
        List<GoalDto> goals;
        Long goalId;
        /** The launch month extra is applied once. */
        boolean opened;

        void load() {
            if (!work.isCurrent() || work.busy()) return;
            if (goals == null) {
                work.run(goalRepository::list, GoalRepository::rows, rows -> {
                    goals = java.util.Collections.unmodifiableList(rows.stream()
                            .filter(g -> "ACTIVE".equals(g.status()))
                            .collect(java.util.stream.Collectors.toList()));
                    // Default to the goal with the nearest target date; goals without a date come last.
                    goalId = goals.stream().min(java.util.Comparator.comparing(
                            (GoalDto g) -> g.targetDate() == null ? "9999-12-31" : g.targetDate()))
                            .map(GoalDto::id).orElse(null);
                    loadAdvice();
                }, false);
                return;
            }
            loadAdvice();
        }

        void selectGoal(Long id) {
            if (work.busy() || reconcile || java.util.Objects.equals(id, goalId)) return;
            goalId = id;
            selected = 0;
            loadAdvice();
        }

        private void loadAdvice() {
            if (!work.isCurrent() || work.busy()) return;
            result = null;
            YearMonth forMonth = month;
            Long forGoal = goalId;
            work.run(() -> repository.advice(forMonth, forGoal), response -> {
                Advice data = AccountRepository.read(response, Advice.class);
                if (!forMonth.toString().equals(data.month()) || data.items() == null
                        || data.items().size() > 12 || data.rating() != null && !validRating(data.rating())
                        || data.items().isEmpty() && (data.id() != null || data.rating() != null)
                        || !data.items().isEmpty() && (data.id() == null || data.id() <= 0)
                        || !(data.verified() && "AI".equals(data.source())
                             || !data.verified() && "RULE".equals(data.source()))) {
                    throw new IOException("Invalid advice delivery");
                }
                for (var item : data.items()) {
                    if (item == null || item.actionId() == null || item.text() == null || item.numbers() == null
                            || item.numbers().stream().anyMatch(f -> f == null || f.value() == null
                                    || f.unit() == null || f.period() == null)) {
                        throw new IOException("Invalid advice item");
                    }
                }
                return data;
            }, data -> { result = data; reconcile = false; }, false);
        }

        void shift(int by) {
            if (work.busy() || reconcile) return;
            YearMonth next = month.plusMonths(by);
            if (next.getYear() < 1000 || next.isAfter(YearMonth.now(ZoneId.of("Asia/Colombo")))) return;
            selected = 0;
            month = next;
            loadAdvice();
        }

        void rate() {
            if (work.busy() || reconcile || result == null || result.id() == null || result.rating() != null
                    || !validRating(selected)) return;
            var previous = result;
            int expected = selected;
            work.run(() -> repository.rate(previous.id(), expected), response -> {
                Rating rating = AccountRepository.read(response, Rating.class);
                if (!Integer.valueOf(expected).equals(rating.rating())) throw new IOException("Invalid rating confirmation");
                return rating;
            }, value -> result = new Advice(previous.month(), previous.id(), previous.source(), previous.verified(),
                    previous.items(), value.rating()), false, failure -> {
                if (!(failure instanceof AccountRepository.HttpFailure http) || http.status >= 500 || http.status == 409) {
                    reconcile = true;
                    work.error = R.string.advice_rating_uncertain;
                }
            });
        }

        private static boolean validRating(int value) { return value >= 1 && value <= 5; }

        @Override protected void onCleared() { work.close(); }
    }
}
