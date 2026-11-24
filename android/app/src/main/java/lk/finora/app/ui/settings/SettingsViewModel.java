package lk.finora.app.ui.settings;

import androidx.lifecycle.ViewModel;

import lk.finora.app.data.repo.MeRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Withdraw consent (T07.5). After success the screen signs the user out locally. */
public final class SettingsViewModel extends ViewModel {
    final Work work = new Work(() -> {});
    private final MeRepository repository = work.requests == null ? null : new MeRepository(work.requests);
    boolean withdrawn;

    void withdrawConsent() {
        if (repository == null || withdrawn) return;
        work.run(repository::withdrawConsent, Work::empty, ignored -> withdrawn = true, false);
    }

    @Override protected void onCleared() { work.close(); }
}
