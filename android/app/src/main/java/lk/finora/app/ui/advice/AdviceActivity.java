package lk.finora.app.ui.advice;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.MonthlyRepository;
import lk.finora.app.data.repo.MonthlyRepository.Advice;
import lk.finora.app.data.repo.MonthlyRepository.Rating;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.budgets.BudgetListActivity;

public final class AdviceActivity extends AppCompatActivity {
    private Model model;
    private LinearLayout page;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(Model.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.finora_surface));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && AccountListActivity.verify(this, model.work) && !model.reconcile) model.load();
    }

    private void render() {
        page.removeAllViews();
        if (!AccountListActivity.verify(this, model.work)) return;
        line(getString(R.string.advice_title), true);
        line(BudgetListActivity.monthLabel(model.month), true);
        if (!model.reconcile) {
            button(R.string.dashboard_previous, () -> model.shift(-1));
            button(R.string.dashboard_next, () -> model.shift(1));
        }
        line(getString(R.string.score_disclaimer), false);
        if (model.work.busy()) line(getString(R.string.records_loading), false);
        if (model.work.error != 0) {
            line(getString(model.work.error), false);
            button(model.reconcile ? R.string.advice_check_rating : R.string.records_retry, model::load);
        }
        if (model.reconcile || model.work.busy() || model.result == null) return;
        if (model.result.items().isEmpty()) {
            line(getString(R.string.advice_empty), false);
            return;
        }
        line(getString(model.result.verified() ? R.string.advice_verified : R.string.advice_rules), true);
        for (var item : model.result.items()) {
            line(adviceText(item), false);
            for (var fact : item.numbers()) {
                line(getString(R.string.advice_figure, fact.value().toPlainString(), fact.unit(), fact.period()), false);
            }
        }
        if (model.result.rating() != null) {
            line(getString(R.string.advice_rated, model.result.rating().toString()), false);
            return;
        }
        line(getString(R.string.advice_rating_prompt), true);
        var options = new RadioGroup(this);
        for (int value = 1; value <= 5; value++) {
            var radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setText(getString(R.string.advice_rating_option, Integer.toString(value)));
            radio.setTextColor(getColor(R.color.finora_ink));
            options.addView(radio);
            if (model.selected == value) options.check(radio.getId());
        }
        page.addView(options);
        Button save = button(R.string.advice_send_rating, model::rate);
        save.setEnabled(model.selected > 0);
        options.setOnCheckedChangeListener((group, id) -> {
            model.selected = group.indexOfChild(group.findViewById(id)) + 1;
            save.setEnabled(model.selected > 0);
        });
    }

    private String adviceText(MonthlyRepository.Item item) {
        if (!"si".equals(getResources().getConfiguration().getLocales().get(0).getLanguage())) return item.text();
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

    private Button button(int title, Runnable action) {
        var control = new Button(this);
        control.setText(title);
        control.setEnabled(!model.work.busy());
        control.setOnClickListener(view -> action.run());
        page.addView(control);
        return control;
    }

    private void line(String value, boolean heading) {
        var label = new TextView(this);
        label.setText(value);
        label.setTextColor(getColor(R.color.finora_ink));
        label.setTextSize(heading ? 20 : 16);
        ViewCompat.setAccessibilityHeading(label, heading);
        page.addView(label);
    }

    public static final class Model extends ViewModel {
        final Work work = new Work(() -> { result = null; selected = 0; reconcile = false; });
        private final MonthlyRepository repository = work.requests == null ? null : new MonthlyRepository(work.requests);
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Colombo"));
        Advice result;
        int selected;
        boolean reconcile;

        void load() {
            if (!work.isCurrent() || work.busy()) return;
            result = null;
            YearMonth forMonth = month;
            work.run(() -> repository.advice(forMonth), response -> {
                Advice data = AccountRepository.read(response, Advice.class);
                if (!forMonth.toString().equals(data.month()) || data.items() == null
                        || data.items().size() > 6 || data.rating() != null && !validRating(data.rating())
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
            load();
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
