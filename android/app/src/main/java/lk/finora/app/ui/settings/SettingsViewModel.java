package lk.finora.app.ui.settings;

import androidx.lifecycle.ViewModel;
import android.content.ContentResolver;
import android.net.Uri;
import java.io.IOException;

import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.MeRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Withdraw consent (T07.5). After success the screen signs the user out locally. */
public final class SettingsViewModel extends ViewModel {
    final Work work = new Work(() -> {});
    private final MeRepository repository = work.requests == null ? null : new MeRepository(work.requests);
    boolean withdrawn;
    boolean deleted;
    boolean exported;
    boolean awaitingExportDestination;

    void withdrawConsent() {
        if (repository == null || withdrawn) return;
        work.run(repository::withdrawConsent, Work::empty, ignored -> withdrawn = true, false);
    }

    void exportData(ContentResolver resolver, Uri destination) {
        if (repository == null) return;
        exported = false;
        work.run(repository::exportData, response -> {
            AccountRepository.requireSuccess(response);
            var body = response.body();
            if (body == null || !work.requests.isCurrent()) throw new IOException("Export unavailable");
            try (var output = resolver.openOutputStream(destination, "wt"); var input = body.byteStream()) {
                if (output == null) throw new IOException("Export destination unavailable");
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (!work.requests.isCurrent()) throw new IOException("Export session ended");
                    output.write(buffer, 0, count);
                }
            }
            return true;
        }, success -> exported = success, false);
    }

    void deleteData(String password) {
        if (repository == null || withdrawn || deleted) return;
        work.run(() -> repository.deleteData(password), Work::empty, ignored -> deleted = true, false);
    }

    @Override protected void onCleared() { work.close(); }
}
