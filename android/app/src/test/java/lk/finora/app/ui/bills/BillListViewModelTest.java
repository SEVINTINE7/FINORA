package lk.finora.app.ui.bills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.data.api.BillDto;
import lk.finora.app.ui.bills.BillListViewModel.Status;

public class BillListViewModelTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test public void paidAlwaysWins() {
        assertEquals(Status.PAID, BillListViewModel.status("2026-11-01", true, TODAY));
        assertEquals(Status.PAID, BillListViewModel.status("2026-11-30", true, TODAY));
    }

    @Test public void unpaidIsOverdueOnlyAfterTheDueDate() {
        assertEquals(Status.OVERDUE, BillListViewModel.status("2026-11-22", false, TODAY));
        assertEquals(Status.DUE, BillListViewModel.status("2026-11-23", false, TODAY)); // due today
        assertEquals(Status.DUE, BillListViewModel.status("2026-12-05", false, TODAY));
    }

    @Test public void eachStatusHasLabelAndColour() {
        assertEquals(R.string.bill_paid, BillListViewModel.statusLabel(Status.PAID));
        assertEquals(R.string.bill_unpaid, BillListViewModel.statusLabel(Status.DUE));
        assertEquals(R.string.bill_overdue, BillListViewModel.statusLabel(Status.OVERDUE));
        assertEquals(R.color.finora_error, BillListViewModel.statusColor(Status.OVERDUE));
    }

    private static BillDto bill(long id, String due, boolean paid) {
        return new BillDto(id, "Bill " + id, new BigDecimal("100.00"), LocalDate.parse(due).getDayOfMonth(), null,
                "2026-11", due, paid);
    }

    @Test public void splitsToPayAndPaidInDueDateOrder() {
        List<BillDto> rows = List.of(bill(1, "2026-11-30", false), bill(2, "2026-11-05", true),
                bill(3, "2026-11-15", false), bill(4, "2026-11-01", true), bill(5, "2026-11-23", false));
        assertEquals(List.of(3L, 5L, 1L), ids(BillListViewModel.toPay(rows)));
        assertEquals(List.of(4L, 2L), ids(BillListViewModel.paidOnes(rows)));
        assertTrue(BillListViewModel.toPay(List.of()).isEmpty());
    }

    @Test public void daysLateCountsWholeDaysAndNeverGoesNegative() {
        assertEquals(8, BillListViewModel.daysLate("2026-11-15", TODAY));
        assertEquals(1, BillListViewModel.daysLate("2026-11-22", TODAY));
        assertEquals(0, BillListViewModel.daysLate("2026-11-23", TODAY));
        assertEquals(0, BillListViewModel.daysLate("2026-11-30", TODAY));
    }

    @Test public void percentPaidIsWholeAndClamped() {
        assertEquals(73, BillListViewModel.percentPaid(summary("33189.00", "24299.00")));
        assertEquals(0, BillListViewModel.percentPaid(summary("0.00", "0.00")));
        assertEquals(100, BillListViewModel.percentPaid(summary("100.00", "100.00")));
        assertEquals(0, BillListViewModel.percentPaid(null));
    }

    private static BillDto.Summary summary(String total, String paid) {
        BigDecimal all = new BigDecimal(total);
        BigDecimal done = new BigDecimal(paid);
        return new BillDto.Summary("2026-11", all, done, all.subtract(done));
    }

    private static List<Long> ids(List<BillDto> rows) {
        List<Long> result = new java.util.ArrayList<>();
        for (BillDto item : rows) result.add(item.id());
        return result;
    }
}
