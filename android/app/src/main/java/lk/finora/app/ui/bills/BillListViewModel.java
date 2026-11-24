package lk.finora.app.ui.bills;

import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.BillDto;
import lk.finora.app.data.repo.AccountRepository.HttpFailure;
import lk.finora.app.data.repo.BillRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Bills of one month with paid toggles and the month's totals (T18.4). */
public final class BillListViewModel extends ViewModel {
    enum Status { PAID, DUE, OVERDUE }

    final Work work = new Work(this::clear);
    private final BillRepository bills = work.requests == null ? null : new BillRepository(work.requests);
    YearMonth month = YearMonth.now();
    List<BillDto> rows = Collections.emptyList();
    BillDto.Summary summary;

    void load() {
        if (bills == null) return;
        String selected = month.toString();
        work.run(() -> bills.list(selected), BillRepository::rows, list -> {
            rows = list;
            work.run(() -> bills.summary(selected), BillRepository::readSummary, value -> summary = value, false);
        }, false);
    }

    void shiftMonth(int months) {
        if (work.busy()) return;
        month = month.plusMonths(months);
        rows = Collections.emptyList();
        summary = null;
        load();
    }

    /** Paid ↔ unpaid for the shown month. "Already paid" (409) just means the list was stale. */
    void togglePaid(BillDto item) {
        work.run(() -> bills.setPaid(item.id(), month.toString(), !item.paid()), BillListViewModel::paidResult,
                ignored -> load(), false);
    }

    void delete(long id) {
        work.run(() -> bills.delete(id), Work::empty, ignored -> load(), false);
    }

    static Void paidResult(okhttp3.Response response) throws java.io.IOException {
        try {
            return Work.empty(response);
        } catch (HttpFailure failure) {
            if (failure.status == 409) return null;
            throw failure;
        }
    }

    /** Paid wins; otherwise a due date before today is overdue. */
    static Status status(String dueDate, boolean paid, LocalDate today) {
        if (paid) return Status.PAID;
        return LocalDate.parse(dueDate).isBefore(today) ? Status.OVERDUE : Status.DUE;
    }

    static int statusLabel(Status status) {
        switch (status) {
            case PAID: return R.string.bill_paid;
            case OVERDUE: return R.string.bill_overdue;
            default: return R.string.bill_unpaid;
        }
    }

    static int statusColor(Status status) {
        switch (status) {
            case PAID: return R.color.finora_green;
            case OVERDUE: return R.color.finora_error;
            default: return R.color.finora_ink;
        }
    }

    private void clear() {
        rows = Collections.emptyList();
        summary = null;
    }

    @Override protected void onCleared() { work.close(); }
}
