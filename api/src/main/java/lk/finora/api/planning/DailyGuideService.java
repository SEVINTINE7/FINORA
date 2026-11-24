package lk.finora.api.planning;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.bill.BillService;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.planning.dto.DailyGuideRequest;
import lk.finora.api.planning.dto.DailyGuideResponse;

/** Read-only, owner-scoped T31 calculation against one committed finance snapshot. */
@Service
public class DailyGuideService {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final List<String> ASSUMPTIONS = List.of(
            "USER_CONFIRMED_AVAILABLE_FUNDS", "USER_CONFIRMED_UNDEDUCTED_SAVINGS",
            "CURRENT_MONTH_OVERDUE_AND_BILLS_THROUGH_INCOME_DATE",
            "ADDITIONAL_UNTRACKED_OR_OLDER_UNPAID_BILLS_DECLARED",
            "PAID_MARKERS_NOT_BANK_PROOF", "TODAY_INCLUDED_INCOME_DAY_EXCLUDED",
            "ROUND_DOWN_LKR_CENTS", "NO_INCOME_ARRIVAL_GUARANTEE");
    private final BillService bills;
    private final JdbcTemplate jdbc;

    public DailyGuideService(BillService bills, JdbcTemplate jdbc) {
        this.bills = bills;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailyGuideResponse calculate(long owner, DailyGuideRequest request) {
        LocalDate today = LocalDate.now(COLOMBO);
        validate(request, today);
        LocalDate next = request.nextIncomeDate();
        LocalDate last = null;
        String source = "USER_SELECTED";
        if (next == null) {
            Date date = jdbc.queryForObject("SELECT MAX(txn_date) FROM transactions "
                    + "WHERE user_id = ? AND type = 'INCOME' AND txn_date <= ?",
                    Date.class, owner, Date.valueOf(today));
            if (date != null) {
                last = date.toLocalDate();
                next = last.plusMonths(1);
                source = "LAST_RECORDED_INCOME_PLUS_ONE_MONTH";
            } else {
                source = "UNAVAILABLE";
            }
        }
        BigDecimal tracked = null;
        Integer count = null;
        DailyGuideMath.Result result;
        if (next == null) {
            result = DailyGuideMath.unavailable(DailyGuideMath.Status.MISSING_NEXT_INCOME_DATE);
        } else if (next.isBefore(today)) {
            result = DailyGuideMath.unavailable(DailyGuideMath.Status.INCOME_DATE_PASSED);
        } else if (next.equals(today)) {
            result = DailyGuideMath.unavailable(DailyGuideMath.Status.INCOME_TODAY);
        } else {
            tracked = DailyGuideMath.ZERO;
            count = 0;
            YearMonth end = YearMonth.from(next);
            for (YearMonth month = YearMonth.from(today); !month.isAfter(end); month = month.plusMonths(1)) {
                for (var bill : bills.listForMonth(owner, month.toString())) {
                    if (!bill.paid() && !bill.dueDate().isAfter(next)) {
                        tracked = tracked.add(bill.amount());
                        count++;
                    }
                }
            }
            if (request.availableFunds() == null) {
                result = DailyGuideMath.unavailable(DailyGuideMath.Status.MISSING_BALANCE);
            } else if (request.plannedSavings() == null) {
                result = DailyGuideMath.unavailable(DailyGuideMath.Status.MISSING_PLANNED_SAVINGS);
            } else if (request.confirmedTrackedUnpaidBills() == null) {
                result = DailyGuideMath.unavailable(DailyGuideMath.Status.BILL_CONFIRMATION_REQUIRED);
            } else if (tracked.compareTo(request.confirmedTrackedUnpaidBills()) != 0) {
                result = DailyGuideMath.unavailable(DailyGuideMath.Status.BILLS_CHANGED);
            } else if (request.additionalUnpaidBills() == null) {
                result = DailyGuideMath.unavailable(DailyGuideMath.Status.MISSING_ADDITIONAL_BILLS);
            } else {
                result = DailyGuideMath.calculate(today, next, request.availableFunds(),
                        tracked.add(request.additionalUnpaidBills()), request.plannedSavings());
            }
        }
        return new DailyGuideResponse(today, next, source, last, request.availableFunds(), tracked, count,
                request.additionalUnpaidBills(), request.plannedSavings(), result.status().name(),
                result.spendingDays(), result.spendableFunds(), result.dailyAllowance(), result.shortfall(),
                result.warning(), ASSUMPTIONS);
    }

    private static void validate(DailyGuideRequest request, LocalDate today) {
        if (request == null) throw ApiException.badRequest("Request required");
        money(request.availableFunds(), true);
        money(request.plannedSavings(), false);
        money(request.confirmedTrackedUnpaidBills(), false);
        money(request.additionalUnpaidBills(), false);
        LocalDate next = request.nextIncomeDate();
        if (next != null && (next.getYear() < 1000 || next.getYear() > 9999 || next.isAfter(today.plusDays(366)))) {
            throw ApiException.badRequest("Next income date must be within the supported date range and no more than 366 days ahead");
        }
    }

    private static void money(BigDecimal value, boolean signed) {
        if (value != null && (value.scale() > 2 || value.abs().compareTo(new BigDecimal("9999999999999.99")) > 0
                || (!signed && value.signum() < 0))) {
            throw ApiException.badRequest("Invalid monetary input");
        }
    }
}
