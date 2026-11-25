package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.validation.Validation;
import lk.finora.api.bill.BillService;
import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.planning.dto.DailyGuideRequest;
import tools.jackson.databind.json.JsonMapper;

class DailyGuideAggregateConfirmationTest {
    private final long owner = 62L;
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Colombo"));
    private final LocalDate incomeDate = today.plusDays(1);
    private final BillService bills = mock(BillService.class);
    private final DailyGuideService service = new DailyGuideService(bills, mock(JdbcTemplate.class));

    @Test
    void returnedAggregateCanBeConfirmedExactlyWithoutExpandingIndividualInputs() {
        String month = YearMonth.from(today).toString();
        var amount = new BigDecimal("5000000000000.00");
        when(bills.listForMonth(owner, month)).thenReturn(List.of(
                bill(1, month, amount), bill(2, month, amount)));

        var quote = service.calculate(owner, request(null));
        assertThat(quote.status()).isEqualTo("BILL_CONFIRMATION_REQUIRED");
        assertThat(quote.trackedUnpaidBills()).isEqualByComparingTo("10000000000000.00");
        assertThat(quote.trackedUnpaidBillCount()).isEqualTo(2);
        var mapper = JsonMapper.builder().build();
        var quoted = mapper.readTree(mapper.writeValueAsString(quote)).get("trackedUnpaidBills").decimalValue();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            assertThat(validation.getValidator().validate(request(quoted))).isEmpty();
            var tooLargeIndividual = new DailyGuideRequest(quoted, BigDecimal.ZERO, incomeDate, quoted, BigDecimal.ZERO);
            assertThat(validation.getValidator().validate(tooLargeIndividual))
                    .singleElement().satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("availableFunds"));
            assertThatThrownBy(() -> service.calculate(owner, tooLargeIndividual)).isInstanceOf(ApiException.class);
        }
        var confirmed = service.calculate(owner, request(quoted));
        assertThat(confirmed.status()).isEqualTo("SHORTFALL");
        assertThat(confirmed.dailyAllowance()).isEqualByComparingTo("0.00");
        assertThat(confirmed.shortfall()).isEqualByComparingTo("10000000000000.00");
        assertThat(service.calculate(owner, request(quoted.subtract(new BigDecimal("0.01")))).status())
                .isEqualTo("BILLS_CHANGED");
    }

    @Test
    void scaleSignAndMaximumStillApplyAtBothBoundaries() {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var validator = validation.getValidator();
            for (var invalid : List.of("-0.01", "0.001", "100000000000000000000000.00")) {
                var request = request(new BigDecimal(invalid));
                assertThat(validator.validate(request)).isNotEmpty();
                assertThatThrownBy(() -> service.calculate(owner, request)).isInstanceOf(ApiException.class);
            }
        }
    }

    private DailyGuideRequest request(BigDecimal confirmation) {
        return new DailyGuideRequest(BigDecimal.ZERO, BigDecimal.ZERO, incomeDate, confirmation, BigDecimal.ZERO);
    }

    private BillMonthResponse bill(long id, String month, BigDecimal amount) {
        return new BillMonthResponse(id, "Synthetic boundary bill", amount, today.getDayOfMonth(),
                null, month, today, false);
    }
}
