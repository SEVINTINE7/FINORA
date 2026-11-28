package lk.finora.app.ui.settings;

import androidx.lifecycle.ViewModel;

import lk.finora.app.R;
import lk.finora.app.data.repo.MeRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/**
 * T68.2/D72 Edit profile. The name is saved with PUT /me (exists). An email change asks for the password,
 * then a code sent to the new address (POST /me/email, then /me/email/confirm: planned in T74).
 */
public final class EditProfileViewModel extends ViewModel {
    final Work work = new Work(() -> {});
    private final MeRepository repository = work.requests == null ? null : new MeRepository(work.requests);
    boolean ready;
    String name;
    String email;
    String language = "EN";
    /** The new address a code was sent to; null until POST /me/email answers 202. */
    String pendingEmail;
    int notice;

    void load() {
        if (repository == null || ready) return;
        work.run(repository::profile, MeRepository::details, this::loaded, false, this::explain);
    }

    private void loaded(MeRepository.Details details) {
        ready = true;
        name = details.fullName() == null ? "" : details.fullName();
        email = details.email() == null ? "" : details.email();
        if (details.language() != null) language = details.language();
    }

    /** Saves the name first when it changed, then asks for the email code when the email changed. */
    void save(String newName, String newEmail, String password) {
        if (repository == null || !ready) return;
        notice = 0;
        boolean emailChanged = ProfileRules.emailChanged(email, newEmail);
        if (ProfileRules.nameChanged(name, newName)) {
            work.run(() -> repository.updateProfile(newName.strip(), language), MeRepository::details, saved -> {
                name = saved.fullName();
                notice = R.string.profile_saved;
                if (emailChanged) requestEmail(newEmail.trim(), password);
            }, false, this::explain);
        } else if (emailChanged) {
            requestEmail(newEmail.trim(), password);
        } else {
            notice = R.string.profile_unchanged;
            work.changed();
        }
    }

    private void requestEmail(String newEmail, String password) {
        work.run(() -> repository.requestEmailChange(newEmail, password), Work::empty,
                ignored -> pendingEmail = newEmail, false, this::explain);
    }

    void confirm(String code) {
        if (repository == null || pendingEmail == null) return;
        notice = 0;
        String target = pendingEmail;
        work.run(() -> repository.confirmEmailChange(target, code), MeRepository::details, saved -> {
            email = saved.email() == null ? target : saved.email();
            pendingEmail = null;
            notice = R.string.profile_email_changed;
        }, false, this::explain);
    }

    private void explain(Exception failure) {
        work.error = ProfileRules.message(failure);
    }

    @Override protected void onCleared() { work.close(); }
}
