package lk.finora.app.ui.dashboard;

import androidx.lifecycle.ViewModel;

import java.time.YearMonth;
import java.time.ZoneId;

import lk.finora.app.data.repo.DashboardRepository;
import lk.finora.app.data.repo.DashboardRepository.Snapshot;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class DashboardViewModel extends ViewModel {
    final Work work = new Work(() -> snapshot = null);
    private final DashboardRepository repository = work.requests == null ? null : new DashboardRepository(work.requests);
    YearMonth month = YearMonth.now(ZoneId.of("Asia/Colombo"));
    Snapshot snapshot;
    private boolean reload;

    public void refresh() {
        if (!work.isCurrent()) return;
        if (work.busy()) {
            reload = true;
            return;
        }
        YearMonth selected = month;
        snapshot = null;
        work.run(() -> repository.load(selected), response -> DashboardRepository.read(response, selected),
                value -> { if (!reload) snapshot = value; }, false);
    }

    void refreshIfQueued() {
        if (work.isCurrent() && !work.busy() && reload) {
            reload = false;
            refresh();
        }
    }

    void step(int direction) {
        if (work.busy() || !canStep(direction)) return;
        month = month.plusMonths(direction);
        refresh();
    }

    boolean canStep(int direction) {
        int year = month.plusMonths(direction).getYear();
        return year >= 1000 && year <= 9999;
    }

    @Override protected void onCleared() { work.close(); }
}
