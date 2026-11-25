package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import lk.finora.api.bill.BillService;
import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.planning.dto.DailyGuideRequest;

class DailyGuideServiceTest {
    @Test
    void explicitDateReservesUnpaidBillsThroughIncomeDayAcrossMonths() {
        long owner = 42L;
        var today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        var current = YearMonth.from(today);
        var next = current.plusMonths(1);
        var incomeDate = next.atDay(15);
        var bills = mock(BillService.class);
        var jdbc = mock(JdbcTemplate.class);

        when(bills.listForMonth(owner, current.toString())).thenReturn(List.of(
                bill(1L, current.atDay(1), "50.00", false),
                bill(2L, current.atEndOfMonth(), "999.00", true)));
        when(bills.listForMonth(owner, next.toString())).thenReturn(List.of(
                bill(1L, next.atDay(1), "50.00", false),
                bill(3L, incomeDate, "100.00", false),
                bill(4L, incomeDate.plusDays(1), "999.00", false)));

        var response = new DailyGuideService(bills, jdbc).calculate(owner,
                new DailyGuideRequest(new BigDecimal("1000.00"), new BigDecimal("100.00"),
                        incomeDate, new BigDecimal("200.00"), new BigDecimal("25.00")));

        verifyNoInteractions(jdbc);
        assertThat(response.asOf()).isEqualTo(today);
        assertThat(response.nextIncomeDate()).isEqualTo(incomeDate);
        assertThat(response.incomeDateSource()).isEqualTo("USER_SELECTED");
        assertThat(response.lastRecordedIncomeDate()).isNull();
        assertThat(response.spendingDays()).isEqualTo((int) ChronoUnit.DAYS.between(today, incomeDate));
        assertThat(response.trackedUnpaidBillCount()).isEqualTo(3);
        assertThat(response.trackedUnpaidBills()).isEqualByComparingTo("200.00");
        assertThat(response.spendableFunds()).isEqualByComparingTo("675.00");
        assertThat(response.status()).isEqualTo("READY");
    }

    private static BillMonthResponse bill(long id, LocalDate due, String amount, boolean paid) {
        return new BillMonthResponse(id, "Synthetic bill", new BigDecimal(amount), due.getDayOfMonth(),
                null, YearMonth.from(due).toString(), due, paid);
    }
}
