package lk.finora.app.ui.planning;

import androidx.lifecycle.ViewModel;

import java.util.List;

import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.data.repo.PlanningRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class PlanningViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final PlanningRepository planning = new PlanningRepository(work.requests);
    List<GoalDto> goals = java.util.Collections.emptyList();
    List<CategoryDto> categories = java.util.Collections.emptyList();
    PlanningRepository.GuideInput guideInput;
    PlanningRepository.Guide guide;
    PlanningRepository.Simulation simulation;
    boolean choicesLoaded;

    void loadChoices() {
        if (choicesLoaded || work.busy() || !work.isCurrent()) return;
        var goalRepository = new GoalRepository(work.requests);
        var categoryRepository = new CategoryRepository(work.requests);
        work.run(goalRepository::list, GoalRepository::rows, rows -> {
            goals = rows;
            work.run(categoryRepository::list, CategoryRepository::rows, values -> {
                categories = values;
                choicesLoaded = true;
            }, false);
        }, false);
    }

    void quote(PlanningRepository.GuideInput input) {
        guide = null;
        guideInput = input;
        work.run(() -> planning.dailyGuide(input), PlanningRepository::readGuide, value -> guide = value, false);
    }

    void simulate(PlanningRepository.SimulationInput input) {
        simulation = null;
        work.run(() -> planning.simulate(input), response -> {
            var value = PlanningRepository.readSimulation(response);
            if (value.goalId() != input.goalId()) throw new java.io.IOException("Mismatched simulation");
            return value;
        }, value -> simulation = value, false);
    }

    private void clear() {
        guideInput = null;
        guide = null;
        simulation = null;
        goals = java.util.Collections.emptyList();
        categories = java.util.Collections.emptyList();
    }

    @Override protected void onCleared() { work.close(); }
}
