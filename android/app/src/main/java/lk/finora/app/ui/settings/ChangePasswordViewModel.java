package lk.finora.app.ui.settings;

import androidx.lifecycle.ViewModel;

import lk.finora.app.data.repo.MeRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** T68.2/D72 Change password: POST /api/v1/me/password (planned in T74), which revokes every session. */
public final class ChangePasswordViewModel extends ViewModel {
    final Work work = new Work(() -> {});
    private final MeRepository repository = work.requests == null ? null : new MeRepository(work.requests);
    boolean changed;

    void change(String current, String next) {
        if (repository == null || changed) return;
        work.run(() -> repository.changePassword(current, next), Work::empty, ignored -> changed = true, false,
                failure -> work.error = ProfileRules.message(failure));
    }

    @Override protected void onCleared() { work.close(); }
}
