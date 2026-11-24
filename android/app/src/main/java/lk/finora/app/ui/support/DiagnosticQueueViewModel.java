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
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Call;

public final class DiagnosticQueueViewModel extends ViewModel {
    private final ProtectedRequests administrator = ProtectedRequests.currentAdmin();
    final boolean admin = administrator != null;
    List<Group> groups = List.of();
    List<Escalation> escalations = List.of();
    int notice;
    final Work work = new Work(admin ? administrator : ProtectedRequests.currentStaff(), () -> {
        groups = List.of();
        escalations = List.of();
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
            }, items -> escalations = items, false);
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
    void setFormatActive(boolean active) {
        write(() -> SmsDiagnosticRepository.setFormatActive(work.requests, active));
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
