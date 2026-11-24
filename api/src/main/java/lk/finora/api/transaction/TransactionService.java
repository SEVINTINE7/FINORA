package lk.finora.api.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.account.Account;
import lk.finora.api.account.AccountRepository;
import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.category.Category;
import lk.finora.api.category.CategoryRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.transaction.dto.SummaryResponse;
import lk.finora.api.transaction.dto.TransactionPage;
import lk.finora.api.transaction.dto.TransactionRequest;
import lk.finora.api.transaction.dto.TransactionResponse;

/** A user's income and expense records (T10). Every method takes the caller's id. */
@Service
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final FinanceWriteLock writeLock;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository,
            CategoryRepository categoryRepository, FinanceWriteLock writeLock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.writeLock = writeLock;
    }

    @Transactional(readOnly = true)
    public TransactionPage list(Long userId, LocalDate from, LocalDate to, TransactionType type, Long categoryId,
            Long accountId, int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        // Newest first; id breaks ties so records on the same day keep a stable order
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("txnDate"), Sort.Order.desc("id")));

        Page<Transaction> result = transactionRepository.search(userId, from, to, type, categoryId, accountId,
                pageRequest);

        List<TransactionResponse> items = new ArrayList<>();
        for (Transaction transaction : result.getContent()) {
            items.add(toResponse(transaction));
        }
        return new TransactionPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return toResponse(findOwn(userId, id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse create(Long userId, TransactionRequest request) {
        writeLock.acquire(userId);
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        copyRequest(userId, request, transaction);
        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    /** Runs the create checks (own, non-archived account; visible category of the same kind) without saving. */
    @Transactional(readOnly = true)
    public void checkNew(Long userId, TransactionRequest request) {
        copyRequest(userId, request, new Transaction());
    }

    /**
     * Saves one imported record (T12) with the same account/category checks as {@link #create}.
     * Source is always CSV; the caller computes the dedup hash.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse createImported(Long userId, TransactionRequest request, String dedupHash) {
        writeLock.acquire(userId);
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        copyRequest(userId, request, transaction);
        transaction.setSource(TransactionSource.CSV);
        transaction.setDedupHash(dedupHash);
        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void createSmsImported(Long userId, TransactionRequest request, String dedupHash) {
        writeLock.acquire(userId);
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        copyRequest(userId, request, transaction);
        transaction.setDedupHash(dedupHash);
        transactionRepository.save(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        writeLock.acquire(userId);
        Transaction transaction = findOwn(userId, id);
        copyRequest(userId, request, transaction);
        transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long userId, Long id) {
        writeLock.acquire(userId);
        transactionRepository.delete(findOwn(userId, id));
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be on or before 'to'");
        }
        // setScale(2) so an empty range shows 0.00, not 0
        BigDecimal income = transactionRepository.sumAmount(userId, TransactionType.INCOME, from, to)
                .setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal expense = transactionRepository.sumAmount(userId, TransactionType.EXPENSE, from, to)
                .setScale(2, RoundingMode.UNNECESSARY);
        return new SummaryResponse(from, to, income, expense, income.subtract(expense));
    }

    /** Checks the account and category, then copies the request fields onto the entity. */
    private void copyRequest(Long userId, TransactionRequest request, Transaction transaction) {
        TransactionType type = TransactionType.valueOf(request.type());

        // The account must be the user's own. Another user's account looks the same as a missing one.
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ApiException.badRequest("Account not found"));
        // Archived accounts cannot get new records, but old records on them can still be edited
        boolean accountChanged = !account.getId().equals(transaction.getAccountId());
        if (account.isArchived() && accountChanged) {
            throw ApiException.badRequest("This account is archived");
        }

        if (request.categoryId() != null) {
            // Must be a default category or the user's own
            Category category = categoryRepository.findVisibleById(request.categoryId(), userId)
                    .orElseThrow(() -> ApiException.badRequest("Category not found"));
            // An expense cannot go into an income category, and the other way round
            if (!category.getKind().name().equals(type.name())) {
                throw ApiException.badRequest("Category is for " + category.getKind() + ", not " + type);
            }
        }

        transaction.setAccountId(account.getId());
        transaction.setCategoryId(request.categoryId());
        transaction.setType(type);
        transaction.setAmount(request.amount());
        transaction.setTxnDate(request.txnDate());
        transaction.setMerchant(trimToNull(request.merchant()));
        transaction.setNote(trimToNull(request.note()));
        if (request.source() != null) {
            transaction.setSource(TransactionSource.valueOf(request.source()));
        }
    }

    // Another user's record gives the same 404 as a missing one (never 403)
    private Transaction findOwn(Long userId, Long id) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
    }

    private String trimToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(t.getId(), t.getAccountId(), t.getCategoryId(), t.getType().name(),
                t.getAmount(), t.getTxnDate(), t.getMerchant(), t.getNote(), t.getSource().name());
    }
}
