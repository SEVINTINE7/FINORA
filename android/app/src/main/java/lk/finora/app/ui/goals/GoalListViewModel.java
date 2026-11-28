package lk.finora.app.ui.goals;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.dashboard.DashboardHighlights;

/** All of the user's goals (T19.3), shown focus goal first, then in progress, reached and cancelled (T64.2). */
public final class GoalListViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final GoalRepository goals = work.requests == null ? null : new GoalRepository(work.requests);
    List<GoalDto> rows = Collections.emptyList();

    void load() {
        if (goals != null) work.run(goals::list, GoalRepository::rows, value -> rows = value, false);
    }

    /** D68: the same focus goal Home shows, or null. */
    static GoalDto focus(List<GoalDto> rows) {
        return DashboardHighlights.focus(rows);
    }

    /** Focus goal first, then the other goals in progress, then reached, then cancelled; API order inside each. */
    static List<GoalDto> ordered(List<GoalDto> rows) {
        GoalDto focus = focus(rows);
        List<GoalDto> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(goal -> goal == focus ? 0 : rank(goal.status())));
        return sorted;
    }

    private static int rank(String status) {
        switch (status) {
            case "DONE": return 2;
            case "CANCELLED": return 3;
            default: return 1;
        }
    }

    /** Totals over goals in progress only: saved, targets, still to save and how many. */
    record Totals(BigDecimal saved, BigDecimal target, BigDecimal toGo, int count) {
        int percent() {
            if (target.signum() <= 0) return 0;
            int value = saved.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.DOWN).intValue();
            return Math.max(0, Math.min(100, value));
        }
    }

    static Totals totals(List<GoalDto> rows) {
        BigDecimal saved = BigDecimal.ZERO.setScale(2);
        BigDecimal target = BigDecimal.ZERO.setScale(2);
        BigDecimal toGo = BigDecimal.ZERO.setScale(2);
        int count = 0;
        for (GoalDto goal : rows) {
            if (!"ACTIVE".equals(goal.status())) continue;
            saved = saved.add(goal.saved());
            target = target.add(goal.targetAmount());
            toGo = toGo.add(goal.remaining());
            count++;
        }
        return new Totals(saved, target, toGo, count);
    }

    private void clear() { rows = Collections.emptyList(); }

    @Override protected void onCleared() { work.close(); }
}
