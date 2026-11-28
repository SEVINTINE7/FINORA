package lk.finora.app.ui.goals;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;

import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Add or edit a goal (T19.3). Editing keeps a cancelled goal cancelled. */
public final class GoalFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final GoalRepository goals = work.requests == null ? null : new GoalRepository(work.requests);
    long id;
    boolean ready;
    boolean saved;
    String name = "";
    String target = "";
    String targetDate = "";
    /** D68 toggle: true for "By a date". A new goal starts with no deadline. */
    boolean hasDeadline;
    /** Already saved toward the goal being edited; zero for a new goal (monthly hint only). */
    BigDecimal savedSoFar = BigDecimal.ZERO;
    private boolean cancelled;

    void load(long goalId) {
        id = goalId;
        if (id == 0) {
            ready = true;
            work.changed();
        } else if (goals != null) {
            work.run(() -> goals.get(id), GoalRepository::readGoal, goal -> {
                name = goal.name();
                target = goal.targetAmount().setScale(2).toPlainString();
                targetDate = goal.targetDate() == null ? "" : goal.targetDate();
                hasDeadline = goal.targetDate() != null;
                savedSoFar = goal.saved();
                cancelled = "CANCELLED".equals(goal.status());
                ready = true;
            }, false);
        }
    }

    void save(String goalName, BigDecimal amount, String date) {
        if (!ready || work.ambiguousCreate || !work.isCurrent() || work.busy()) return;
        GoalDto.Write body = body(goalName, amount, date, id != 0 && cancelled);
        work.run(() -> id == 0 ? goals.create(body) : goals.update(id, body), GoalRepository::readGoal,
                value -> saved = true, id == 0);
    }

    /** Empty date means "no target date". cancelled is sent only when it must be kept (PUT would resume it). */
    static GoalDto.Write body(String goalName, BigDecimal amount, String date, boolean keepCancelled) {
        String trimmed = date.trim();
        return new GoalDto.Write(goalName.trim(), amount, trimmed.isEmpty() ? null : trimmed,
                keepCancelled ? Boolean.TRUE : null);
    }

    static boolean validName(String value) {
        String text = value.trim();
        return !text.isEmpty() && text.length() <= 60;
    }

    private void clear() {
        name = "";
        target = "";
        targetDate = "";
        hasDeadline = false;
        savedSoFar = BigDecimal.ZERO;
        ready = false;
    }

    @Override protected void onCleared() { work.close(); }
}
