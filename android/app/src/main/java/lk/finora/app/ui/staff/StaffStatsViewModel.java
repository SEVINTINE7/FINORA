package lk.finora.app.ui.staff;

import androidx.lifecycle.ViewModel;
import java.io.IOException;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.StaffStatsRepository;
import lk.finora.app.data.repo.StaffStatsRepository.Stats;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class StaffStatsViewModel extends ViewModel {
    Stats stats;
    final Work work = new Work(ProtectedRequests.currentStaff(), () -> stats = null);
    private final StaffStatsRepository repository = new StaffStatsRepository(work.requests);

    void load() {
        work.run(repository::fetch, response -> {
            Stats result = AccountRepository.read(response, Stats.class);
            if (result.myOpenTickets() == null || result.myOpenTickets() < 0
                    || result.unassignedTickets() == null || result.unassignedTickets() < 0) {
                throw new IOException("Invalid staff queue counts");
            }
            return result;
        }, value -> stats = value, false);
    }

    @Override protected void onCleared() { work.close(); }
}
