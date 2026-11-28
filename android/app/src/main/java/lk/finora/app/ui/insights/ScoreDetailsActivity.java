package lk.finora.app.ui.insights;

import android.os.Bundle;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityScoreDetailsBinding;
import lk.finora.app.ui.FinancialDatePicker;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/**
 * T66.2/D70 Score details: the month's emergency reserve (amount + calendar date) and the debt and goal questions.
 * The page asks the positive question; {@link ScoreRules#noneFromAnswer} keeps the stored value unchanged.
 */
public final class ScoreDetailsActivity extends SecureActivity {
    public static final String MONTH = "lk.finora.app.SCORE_MONTH";
    private ScoreDetailsViewModel model;
    private ActivityScoreDetailsBinding view;

    @Override protected void onCreate(Bundle state) {
        FinancialDatePicker.protect(this);
        super.onCreate(state);
        model = new ViewModelProvider(this).get(ScoreDetailsViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityScoreDetailsBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.scoreHeading, true);
        if (model.month == null) {
            String extra = getIntent().getStringExtra(MONTH);
            model.month = extra == null ? YearMonth.now(ZoneId.of("Asia/Colombo")) : YearMonth.parse(extra);
        }
        String month = model.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale()));
        view.scoreIntro.setText(getString(R.string.score_details_intro, month));
        view.scoreDateHelp.setText(getString(R.string.score_reserve_date_help, month));
        tiles(view.scoreDebt);
        tiles(view.scoreGoals);
        view.scoreDebt.onSelectionChanged(() -> { if (model.filled) model.debt = view.scoreDebt.getSelectedItemPosition(); });
        view.scoreGoals.onSelectionChanged(() -> { if (model.filled) model.goals = view.scoreGoals.getSelectedItemPosition(); });
        AccountFormActivity.watch(view.scoreReserve, text -> { if (model.filled) model.amount = text; });
        view.scoreDate.setOnClickListener(ignored -> FinancialDatePicker.show(this, R.string.score_reserve_date,
                ScoreRules.pickerStart(model.date, model.month, today()), this::selectDate));
        view.scoreDateClear.setOnClickListener(ignored -> { model.date = ""; showDate(); });
        FinancialDatePicker.restore(this, this::selectDate);
        view.scoreSave.setOnClickListener(ignored -> save());
        view.scoreRetry.setOnClickListener(ignored -> model.load(model.month));
        model.work.changes.observe(this, ignored -> render());
        if (model.declaration == null && !model.work.busy()) model.load(model.month);
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }

    private Locale locale() { return getResources().getConfiguration().getLocales().get(0); }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Colombo")); }

    private void tiles(FormChoices choices) {
        AccountFormActivity.choices(this, choices.getId(), R.string.score_unknown, R.string.score_yes, R.string.score_no);
        choices.setColumns(3);
    }

    private void selectDate(LocalDate date) {
        model.date = date.toString();
        showDate();
    }

    private void showDate() {
        boolean chosen = model.date != null && !model.date.isEmpty();
        String text;
        try {
            text = chosen ? LocalDate.parse(model.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    .withLocale(locale())) : getString(R.string.score_reserve_pick);
        } catch (RuntimeException invalid) {
            text = model.date;
        }
        view.scoreDate.setText(text);
        view.scoreDate.setContentDescription(getString(R.string.score_reserve_date) + ": " + text);
        view.scoreDateClear.setVisibility(chosen && view.scoreDate.isEnabled() ? View.VISIBLE : View.GONE);
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        boolean ready = model.declaration != null;
        if (ready && !model.filled) {
            view.scoreReserve.setText(model.amount);
            view.scoreDebt.setSelection(model.debt);
            view.scoreGoals.setSelection(model.goals);
            model.filled = true;
        }
        boolean enabled = ready && !model.work.busy();
        view.scoreReserve.setEnabled(enabled);
        view.scoreDate.setEnabled(enabled);
        view.scoreDebt.setEnabled(enabled);
        view.scoreGoals.setEnabled(enabled);
        view.scoreSave.setEnabled(enabled);
        int message = model.work.busy() ? R.string.records_loading : model.work.error;
        view.scoreStatus.setText(message == 0 ? "" : getString(message));
        view.scoreStatus.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        view.scoreRetry.setVisibility(!model.work.busy() && model.work.error != 0 && !ready ? View.VISIBLE : View.GONE);
        showDate();
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || model.declaration == null || model.work.busy()) return;
        try {
            var values = ScoreRules.parse(view.scoreReserve.getText().toString(), model.date == null ? "" : model.date,
                    model.month, today(), ScoreRules.noneFromAnswer(view.scoreDebt.getSelectedItemPosition()),
                    ScoreRules.noneFromAnswer(view.scoreGoals.getSelectedItemPosition()));
            model.save(values);
        } catch (IllegalArgumentException invalid) {
            FormFields.error(view.scoreReserve, R.string.score_input_invalid);
        }
    }
}
