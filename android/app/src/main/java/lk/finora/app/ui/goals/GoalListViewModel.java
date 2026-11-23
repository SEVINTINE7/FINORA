package lk.finora.app.ui.goals;

import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** All of the user's goals, oldest first (T19.3). */
public final class GoalListViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final GoalRepository goals = work.requests == null ? null : new GoalRepository(work.requests);
    List<GoalDto> rows = Collections.emptyList();

    void load() {
        if (goals != null) work.run(goals::list, GoalRepository::rows, value -> rows = value, false);
    }

    private void clear() { rows = Collections.emptyList(); }

    @Override protected void onCleared() { work.close(); }
}
