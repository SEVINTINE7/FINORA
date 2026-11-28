package lk.finora.app.ui.insights;

import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;

import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.InsightsRepository;
import lk.finora.app.data.repo.InsightsRepository.Insight;
import lk.finora.app.data.repo.InsightsRepository.Score;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class InsightsViewModel extends ViewModel {
    final Work patternsWork = new Work(() -> patterns = null);
    final Work scoreWork = new Work(() -> score = null);
    private final InsightsRepository repository = patternsWork.requests == null ? null
            : new InsightsRepository(patternsWork.requests);
    YearMonth month = YearMonth.now(ZoneId.of("Asia/Colombo"));
    Insight[] patterns;
    Score score;
    boolean busy() { return patternsWork.busy() || scoreWork.busy(); }

    void shift(int by) {
        if (busy()) return;
        int year = month.plusMonths(by).getYear();
        if (year < 1000 || year > 9999) return;
        month = month.plusMonths(by);
        load();
    }

    void load() {
        if (!patternsWork.isCurrent() || busy()) return;
        patterns = null;
        score = null;
        var selected = month;
        patternsWork.run(() -> repository.insights(selected), response -> {
            var rows = AccountRepository.read(response, Insight[].class);
            for (var row : rows) {
                if (row == null || !selected.toString().equals(row.month())
                        || row.type() == null || row.from() == null || row.to() == null || row.facts() == null) {
                    throw new IOException("Invalid insights response");
                }
            }
            return rows;
        }, rows -> patterns = rows, false);
        loadScore();
    }

    private void loadScore() {
        YearMonth selected = month;
        score = null;
        scoreWork.run(() -> repository.score(selected), response -> {
            var value = AccountRepository.read(response, Score.class);
            if (!selected.toString().equals(value.month()) || !"D20-v1".equals(value.modelVersion())
                    || value.parts() == null || value.parts().length != 5
                    || value.total() != null && (value.total() < 0 || value.total() > 100)) {
                throw new IOException("Invalid score");
            }
            var keys = new HashSet<String>();
            boolean missing = false;
            for (var part : value.parts()) {
                if (part == null || part.facts() == null || part.reasonKey() == null
                        || part.weight() != weight(part.key()) || !keys.add(part.key())
                        || !validPoints(part)) throw new IOException("Invalid component");
                missing |= "UNAVAILABLE".equals(part.status());
            }
            if (missing != (value.total() == null)) throw new IOException("Invalid score availability");
            return value;
        }, value -> score = value, false);
    }

    private static int weight(String key) {
        if (key == null) return -1;
        return switch (key) {
            case "SPENDING_CONTROL" -> 45;
            case "DEBT_PAYMENTS" -> 20;
            case "GOAL_PROGRESS" -> 15;
            case "SAVINGS", "EMERGENCY_SAVINGS" -> 10;
            default -> -1;
        };
    }

    private static boolean validPoints(InsightsRepository.Part part) {
        if ("SCORED".equals(part.status())) {
            return part.points() != null && part.points().signum() >= 0
                    && part.points().compareTo(java.math.BigDecimal.valueOf(part.weight())) <= 0;
        }
        return part.points() == null
                && ("UNAVAILABLE".equals(part.status()) || "NOT_APPLICABLE".equals(part.status()));
    }

    @Override protected void onCleared() {
        patternsWork.close();
        scoreWork.close();
    }
}
