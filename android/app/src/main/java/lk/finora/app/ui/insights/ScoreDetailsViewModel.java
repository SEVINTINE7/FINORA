package lk.finora.app.ui.insights;

import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.time.YearMonth;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.InsightsRepository;
import lk.finora.app.data.repo.InsightsRepository.Declaration;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Response;

/** T66.2/D70 Score details: loads the month's declarations, keeps the draft across rotation, saves them. */
public final class ScoreDetailsViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final InsightsRepository repository = work.requests == null ? null : new InsightsRepository(work.requests);
    YearMonth month;
    Declaration declaration;
    boolean filled;
    boolean saved;
    String amount = "";
    String date = "";
    int debt = ScoreRules.NOT_SURE;
    int goals = ScoreRules.NOT_SURE;

    void load(YearMonth selected) {
        if (!work.isCurrent() || work.busy() || repository == null) return;
        month = selected;
        declaration = null;
        filled = false;
        work.run(() -> repository.declaration(selected), response -> read(response, selected), value -> {
            declaration = value;
            amount = value.emergencyReserve() == null ? "" : value.emergencyReserve().toPlainString();
            date = value.reserveAsOf() == null ? "" : value.reserveAsOf();
            debt = ScoreRules.answerFromNone(value.noApplicableDebt());
            goals = ScoreRules.answerFromNone(value.noApplicableGoals());
        }, false);
    }

    void save(Declaration values) {
        if (work.busy() || declaration == null || !work.isCurrent()) return;
        var selected = month;
        work.run(() -> repository.declaration(selected, values), response -> read(response, selected),
                value -> saved = true, false, failure -> {
                    // Refetch persisted values before any later write if the outcome may be uncertain.
                    if (!(failure instanceof AccountRepository.HttpFailure http) || http.status >= 500) {
                        declaration = null;
                        work.error = R.string.score_save_uncertain;
                    }
                });
    }

    private static Declaration read(Response response, YearMonth month) throws IOException {
        var value = AccountRepository.read(response, InsightsRepository.DeclarationResult.class);
        if (!month.toString().equals(value.month())) throw new IOException("Invalid declaration month");
        return value.values();
    }

    private void clear() {
        declaration = null;
        filled = false;
        amount = "";
        date = "";
        debt = ScoreRules.NOT_SURE;
        goals = ScoreRules.NOT_SURE;
    }

    @Override protected void onCleared() { work.close(); }
}
