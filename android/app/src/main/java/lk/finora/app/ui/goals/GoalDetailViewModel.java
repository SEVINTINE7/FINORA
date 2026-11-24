package lk.finora.app.ui.goals;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** One goal, its contributions, add/remove contribution, cancel/resume and delete (T19.3). */
public final class GoalDetailViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final GoalRepository goals = work.requests == null ? null : new GoalRepository(work.requests);
    long id;
    GoalDto goal;
    List<GoalDto.Contribution> contributions = Collections.emptyList();
    boolean deleted;
    /** Set after a contribution is saved so the screen can clear the amount field once. */
    boolean contributionAdded;

    void load(long goalId) {
        id = goalId;
        if (goals == null) return;
        work.run(() -> goals.get(id), GoalRepository::readGoal, value -> {
            goal = value;
            loadContributions();
        }, false);
    }

    private void loadContributions() {
        work.run(() -> goals.contributions(id), GoalRepository::readContributions,
                value -> contributions = value, false);
    }

    /** Not a create of a new record in the list sense, but a POST: an unclear result must not be retried blindly. */
    void addContribution(BigDecimal amount, String date) {
        if (goal == null || work.ambiguousCreate) return;
        work.run(() -> goals.addContribution(id, new GoalDto.NewContribution(amount, date)),
                GoalRepository::readGoal, value -> {
                    goal = value;
                    contributionAdded = true;
                    loadContributions();
                }, true);
    }

    void deleteContribution(long contributionId) {
        work.run(() -> goals.deleteContribution(id, contributionId), GoalRepository::readGoal, value -> {
            goal = value;
            loadContributions();
        }, false);
    }

    /** Cancel or resume keeps every other field as it is (PUT replaces the whole goal). */
    void setCancelled(boolean cancelled) {
        if (goal == null) return;
        GoalDto.Write body = new GoalDto.Write(goal.name(), goal.targetAmount(), goal.targetDate(), cancelled);
        work.run(() -> goals.update(id, body), GoalRepository::readGoal, value -> goal = value, false);
    }

    void delete() {
        work.run(() -> goals.delete(id), Work::empty, ignored -> deleted = true, false);
    }

    private void clear() {
        goal = null;
        contributions = Collections.emptyList();
    }

    @Override protected void onCleared() { work.close(); }
}
