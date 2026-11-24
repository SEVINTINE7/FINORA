package lk.finora.app.ui.support;

import androidx.lifecycle.ViewModel;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Escalation;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Group;
import lk.finora.app.data.repo.SmsDiagnosticRepository.FormatRevision;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Call;

public final class DiagnosticQueueViewModel extends ViewModel {
    private final ProtectedRequests administrator = ProtectedRequests.currentAdmin();
    final boolean admin = administrator != null;
    List<Group> groups = List.of();
    List<Escalation> escalations = List.of();
    List<FormatRevision> revisions = List.of();
    int notice;
    final Work work = new Work(admin ? administrator : ProtectedRequests.currentStaff(), () -> {
        groups = List.of();
        escalations = List.of();
        revisions = List.of();
        notice = 0;
    });

    void load() {
        if (admin) {
            work.run(() -> SmsDiagnosticRepository.escalations(work.requests), response -> {
                var items = AccountRepository.read(response, Escalation[].class);
                for (var item : items) {
                    if (item == null || item.id() < 1 || item.errorCode() == null
                            || !bankValid(item.bankCode())
                            || (!"OPEN".equals(item.status()) && !"ACKNOWLEDGED".equals(item.status()))) {
                        throw new IOException("Invalid escalation response");
                    }
                }
                return List.of(items);
            }, items -> {
                escalations = items;
                work.run(() -> SmsDiagnosticRepository.revisions(work.requests), response -> {
                    var rows = AccountRepository.read(response, FormatRevision[].class);
                    for (var row : rows) {
                        if (row == null || row.version() < 1 || (row.active() && !row.published())) {
                            throw new IOException("Invalid format revision");
                        }
                    }
                    return List.of(rows);
                }, values -> revisions = values, false);
            }, false);
        } else {
            work.run(() -> SmsDiagnosticRepository.groups(work.requests), response -> {
                var items = AccountRepository.read(response, Group[].class);
                for (var item : items) {
                    if (item == null || item.reports() < 1 || item.errorCode() == null
                            || !bankValid(item.bankCode())
                            || (item.formatVersion() != null && (item.bankCode() == null || item.formatVersion() != 1))) {
                        throw new IOException("Invalid diagnostic group response");
                    }
                }
                return List.of(items);
            }, items -> groups = items, false);
        }
    }

    void flag(Group group) { write(() -> SmsDiagnosticRepository.flag(work.requests, group)); }
    void advance(Escalation item) { write(() -> SmsDiagnosticRepository.advance(work.requests, item)); }
    void deactivate(int revision) {
        write(() -> SmsDiagnosticRepository.changeRevision(work.requests, revision, "deactivate"));
    }
    void draft(int revision) { write(() -> SmsDiagnosticRepository.changeRevision(work.requests, revision, "")); }
    void discard(int revision) { write(() -> SmsDiagnosticRepository.discardRevision(work.requests, revision)); }
    void publish(int revision) {
        write(() -> SmsDiagnosticRepository.changeRevision(work.requests, revision, "publish"));
    }
    void activate(int revision) {
        write(() -> SmsDiagnosticRepository.changeRevision(work.requests, revision, "activate"));
    }

    private void write(Supplier<Call> request) {
        notice = 0;
        work.run(request, Work::empty, ignored -> {
            notice = R.string.sms_diagnostic_updated;
            load();
        }, false);
    }

    private static boolean bankValid(String bank) { return bank == null || "NSB".equals(bank); }
    @Override protected void onCleared() { work.close(); }
}
