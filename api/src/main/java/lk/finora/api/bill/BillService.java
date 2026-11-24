package lk.finora.api.bill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.history.HistoryInvalidation;
import lk.finora.api.bill.dto.BillRequest;
import lk.finora.api.bill.dto.BillResponse;
import lk.finora.api.bill.dto.BillSummaryResponse;
import lk.finora.api.budget.dto.BudgetRequest;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryKind;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;

/** Monthly bills and their paid / unpaid state per month (T18). Every method takes the caller's id. */
@Service
public class BillService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final CategoryRepository categoryRepository;
    private final FinanceWriteLock writeLock;
    private final HistoryInvalidation history;

    public BillService(BillRepository billRepository, BillPaymentRepository billPaymentRepository,
            CategoryRepository categoryRepository, FinanceWriteLock writeLock, HistoryInvalidation history) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.categoryRepository = categoryRepository;
        this.writeLock = writeLock;
        this.history = history;
    }

    // ---------- CRUD (T18.1) ----------

    @Transactional(readOnly = true)
    public BillResponse get(Long userId, Long id) {
        return toResponse(findOwn(userId, id));
    }

    @Transactional
    public BillResponse create(Long userId, BillRequest request) {
        writeLock.acquire(userId);
        checkCategory(userId, request.categoryId());
        Bill bill = new Bill();
        bill.setUserId(userId);
        copyRequest(request, bill);
        billRepository.save(bill);
        history.allMonthsChanged(userId);
        return toResponse(bill);
    }

    @Transactional
    public BillResponse update(Long userId, Long id, BillRequest request) {
        writeLock.acquire(userId);
        Bill bill = findOwn(userId, id);
        checkCategory(userId, request.categoryId());
        copyRequest(request, bill);
        billRepository.save(bill);
        history.allMonthsChanged(userId);
        return toResponse(bill);
    }

    /** Deletes the bill. Its paid markers are removed by the database (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long userId, Long id) {
        writeLock.acquire(userId);
        billRepository.delete(findOwn(userId, id));
        history.allMonthsChanged(userId);
    }

    // ---------- one month (T18.2) ----------

    /** All bills with their due date in that month and whether they are paid. Earliest due first. */
    @Transactional(readOnly = true)
    public List<BillMonthResponse> listForMonth(Long userId, String month) {
        YearMonth yearMonth = parseMonth(month);
        List<BillMonthResponse> result = new ArrayList<>();
        for (Bill bill : billRepository.findByUserIdAndActiveTrueOrderByDueDayAscIdAsc(userId)) {
            boolean paid = billPaymentRepository.existsByBillIdAndMonth(bill.getId(), month);
            result.add(new BillMonthResponse(bill.getId(), bill.getName(), bill.getAmount(), bill.getDueDay(),
                    bill.getCategoryId(), month, dueDate(bill, yearMonth), paid));
        }
        return result;
    }

    /** Marks the bill paid for the month. Paying the same month twice gives 409. */
    @Transactional
    public void markPaid(Long userId, Long id, String month) {
        writeLock.acquire(userId);
        parseMonth(month);
        Bill bill = findOwn(userId, id);
        if (billPaymentRepository.existsByBillIdAndMonth(bill.getId(), month)) {
            throw new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT, "This bill is already paid for " + month);
        }
        BillPayment payment = new BillPayment();
        payment.setBillId(bill.getId());
        payment.setMonth(month);
        billPaymentRepository.save(payment);
        history.monthChanged(userId, month);
    }

    /** Marks the bill unpaid for the month. Already unpaid is fine (nothing to do). */
    @Transactional
    public void markUnpaid(Long userId, Long id, String month) {
        writeLock.acquire(userId);
        parseMonth(month);
        Bill bill = findOwn(userId, id);
        billPaymentRepository.findByBillIdAndMonth(bill.getId(), month)
                .ifPresent(payment -> {
                    billPaymentRepository.delete(payment);
                    history.monthChanged(userId, month);
                });
    }

    /**
     * Due date of the bill in a month. A due day past the end of the month moves to the last day,
     * e.g. due day 31 in February 2026 is 28 February, in a leap year 29 February.
     */
    public static LocalDate dueDate(Bill bill, YearMonth month) {
        int day = Math.min(bill.getDueDay(), month.lengthOfMonth());
        return month.atDay(day);
    }

    // ---------- totals (T18.3) ----------

    /** Total of all the user's bills in one month. For budgets and planning (T21, T31). */
    @Transactional(readOnly = true)
    public BigDecimal monthlyTotal(Long userId) {
        return billRepository.sumActiveAmount(userId).setScale(2, RoundingMode.UNNECESSARY);
    }

    @Transactional(readOnly = true)
    public BillSummaryResponse summary(Long userId, String month) {
        BigDecimal paid = BigDecimal.ZERO.setScale(2);
        for (BillMonthResponse bill : listForMonth(userId, month)) {
            if (bill.paid()) {
                paid = paid.add(bill.amount());
            }
        }
        BigDecimal total = monthlyTotal(userId);
        return new BillSummaryResponse(month, total, paid, total.subtract(paid));
    }

    // ---------- helpers ----------

    private void copyRequest(BillRequest request, Bill bill) {
        bill.setName(request.name().trim());
        bill.setAmount(request.amount());
        bill.setDueDay(request.dueDay());
        bill.setCategoryId(request.categoryId());
    }

    /** Optional category: if given it must be a default or own EXPENSE category. */
    private void checkCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        Category category = categoryRepository.findVisibleById(categoryId, userId)
                .orElseThrow(() -> ApiException.badRequest("Category not found"));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw ApiException.badRequest("Bills can only use expense categories");
        }
    }

    // Same YYYY-MM rule as budgets
    private YearMonth parseMonth(String month) {
        if (month == null || !month.matches(BudgetRequest.MONTH_PATTERN)) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        return YearMonth.parse(month);
    }

    // Another user's bill gives the same 404 as a missing one (never 403)
    private Bill findOwn(Long userId, Long id) {
        return billRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Bill not found"));
    }

    private BillResponse toResponse(Bill bill) {
        return new BillResponse(bill.getId(), bill.getName(), bill.getAmount(), bill.getDueDay(),
                bill.getCategoryId());
    }
}
