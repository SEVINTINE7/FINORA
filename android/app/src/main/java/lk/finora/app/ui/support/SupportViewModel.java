package lk.finora.app.ui.support;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import androidx.lifecycle.ViewModel;
import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SupportRepository;
import lk.finora.app.data.repo.SupportRepository.Reply;
import lk.finora.app.data.repo.SupportRepository.Revision;
import lk.finora.app.data.repo.SupportRepository.Ticket;
import lk.finora.app.data.repo.SupportRepository.Topic;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Call;

public final class SupportViewModel extends ViewModel {
    private final ProtectedRequests staffRequests = ProtectedRequests.currentStaff();
    final boolean staff = staffRequests != null;
    List<Ticket> tickets = List.of();
    List<Reply> replies = List.of();
    List<Revision> catalog = List.of();
    long viewedTicket;
    boolean showCatalog;
    int notice;
    final Work work = new Work(staff ? staffRequests : ProtectedRequests.currentUser(), () -> {
        tickets = List.of();
        replies = List.of();
        catalog = List.of();
        notice = 0;
        viewedTicket = 0;
    });
    private final SupportRepository repository = new SupportRepository(work.requests);

    void load() {
        if (showCatalog) {
            work.run(repository::catalog, response -> {
                var values = AccountRepository.read(response, Revision[].class);
                for (var value : values) if (value == null || value.id() < 1 || value.topic() == null
                        || value.english() == null || value.sinhala() == null || value.state() == null) {
                    throw new IOException("Invalid saved reply");
                }
                return List.of(values);
            }, values -> catalog = values, false);
        } else if (viewedTicket > 0) {
            work.run(() -> repository.replies(viewedTicket),
                    response -> List.of(AccountRepository.read(response, Reply[].class)),
                    values -> replies = values, false);
        } else {
            work.run(() -> repository.tickets(staff), response -> {
                var values = AccountRepository.read(response, Ticket[].class);
                for (var value : values) if (value == null || value.id() < 1 || value.status() == null) {
                    throw new IOException("Invalid support ticket");
                }
                return List.of(values);
            }, values -> tickets = values, false);
        }
    }

    void openReplies(long id) { viewedTicket = id; replies = List.of(); load(); }
    void catalog(boolean visible) { viewedTicket = 0; showCatalog = visible; load(); }
    void create(Topic topic) { write(() -> repository.create(topic), true); }
    void claim(long id) { write(() -> repository.claim(id), false); }
    void close(long id) { write(() -> repository.close(id), false); }
    void reply(long id, long revision, String language) { write(() -> repository.reply(id, revision, language), true); }
    void draft(Topic topic) { write(() -> repository.draft(topic), true); }
    void approve(long id) { write(() -> repository.approve(id), false); }
    void retire(long id) { write(() -> repository.retire(id), false); }

    void browseReplies() {
        work.run(repository::catalog, response -> List.of(AccountRepository.read(response, Revision[].class)),
                values -> catalog = values, false);
    }

    private void write(Supplier<Call> call, boolean creation) {
        notice = 0;
        work.run(call, Work::empty, ignored -> {
            notice = R.string.sms_diagnostic_updated;
            load();
        }, creation);
    }

    @Override protected void onCleared() { work.close(); }
}
