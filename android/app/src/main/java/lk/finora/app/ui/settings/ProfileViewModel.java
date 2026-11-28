package lk.finora.app.ui.settings;

import androidx.lifecycle.ViewModel;

import lk.finora.app.data.repo.MeRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** T68: the signed-in user's name and email for the profile card (GET /api/v1/me). */
public final class ProfileViewModel extends ViewModel {
    public final Work work = new Work(() -> details = null);
    private final MeRepository repository = work.requests == null ? null : new MeRepository(work.requests);
    public MeRepository.Details details;

    /** Loads again on every call (e.g. after Edit profile), unless a load is already running. */
    public void load() {
        if (repository == null || work.busy()) return;
        work.run(repository::profile, MeRepository::details, loaded -> details = loaded, false);
    }

    @Override protected void onCleared() { work.close(); }
}
