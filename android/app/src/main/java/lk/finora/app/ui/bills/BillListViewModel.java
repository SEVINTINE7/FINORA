package lk.finora.app.ui.bills;

import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    /** T63.3: bills still to pay, earliest due first (overdue ones come first naturally). */
    static List<BillDto> toPay(List<BillDto> rows) {
        List<BillDto> result = new ArrayList<>();
        for (BillDto item : rows) if (!item.paid()) result.add(item);
        result.sort(Comparator.comparing(BillDto::dueDate));
        return result;
    }

    /** T63.3: bills paid this month, in due-date order. */
    static List<BillDto> paidOnes(List<BillDto> rows) {
        List<BillDto> result = new ArrayList<>();
        for (BillDto item : rows) if (item.paid()) result.add(item);
        result.sort(Comparator.comparing(BillDto::dueDate));
        return result;
    }

    /** Whole days past the due date (0 when not late). */
    static int daysLate(String dueDate, LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(dueDate), today);
        return (int) Math.max(0, days);
    }

    /** Whole percent of the month's bill total already paid, 0–100. */
    static int percentPaid(BillDto.Summary summary) {
        if (summary == null || summary.total().signum() <= 0) return 0;
        return summary.paid().multiply(java.math.BigDecimal.valueOf(100))
                .divide(summary.total(), 0, java.math.RoundingMode.DOWN).max(java.math.BigDecimal.ZERO)
                .min(java.math.BigDecimal.valueOf(100)).intValue();
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
