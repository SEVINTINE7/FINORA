package lk.finora.app.ui.bills;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;

import org.junit.Test;

import lk.finora.app.R;
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
}
