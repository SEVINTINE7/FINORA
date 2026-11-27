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

import jakarta.validation.Validation;
import lk.finora.api.bill.BillService;
import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.planning.dto.DailyGuideRequest;
import tools.jackson.databind.json.JsonMapper;

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

    @Test
    void validBillsCanRoundTripAnAggregateAboveTheIndividualLimit() {
        long owner = 42L;
        var today = LocalDate.now(ZoneId.of("Asia/Colombo"));
        var month = YearMonth.from(today);
        var incomeDate = today.plusDays(1);
        var bills = mock(BillService.class);
        var service = new DailyGuideService(bills, mock(JdbcTemplate.class));
        when(bills.listForMonth(owner, month.toString())).thenReturn(List.of(
                bill(1L, today, "5000000000000.00", false),
                bill(2L, today, "5000000000000.00", false)));

        var quote = service.calculate(owner, confirmation(incomeDate, null));
        assertThat(quote.status()).isEqualTo("BILL_CONFIRMATION_REQUIRED");
        assertThat(quote.trackedUnpaidBills()).isEqualTo(new BigDecimal("10000000000000.00"));
        assertThat(quote.trackedUnpaidBillCount()).isEqualTo(2);
        assertThat(JsonMapper.builder().build().writeValueAsString(quote))
                .contains("\"trackedUnpaidBills\":10000000000000.00");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(confirmation(incomeDate, new BigDecimal("9999999999999.99"))))
                    .isEmpty();
            assertThat(validator.validate(confirmation(incomeDate, quote.trackedUnpaidBills()))).isEmpty();
        }
        var accepted = service.calculate(owner, confirmation(incomeDate, quote.trackedUnpaidBills()));
        assertThat(accepted.status()).isEqualTo("SHORTFALL");
        assertThat(accepted.shortfall()).isEqualByComparingTo("10000000000000.00");

        var changed = service.calculate(owner, confirmation(incomeDate,
                quote.trackedUnpaidBills().subtract(new BigDecimal("0.01"))));
        assertThat(changed.status()).isEqualTo("BILLS_CHANGED");
    }

    private static DailyGuideRequest confirmation(LocalDate nextIncomeDate, BigDecimal trackedTotal) {
        return new DailyGuideRequest(BigDecimal.ZERO, BigDecimal.ZERO, nextIncomeDate, trackedTotal,
                BigDecimal.ZERO);
    }

    private static BillMonthResponse bill(long id, LocalDate due, String amount, boolean paid) {
        return new BillMonthResponse(id, "Synthetic bill", new BigDecimal(amount), due.getDayOfMonth(),
                null, YearMonth.from(due).toString(), due, paid);
    }
}
