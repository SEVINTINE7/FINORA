package lk.finora.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Ownership check: only finds the record if it belongs to this user
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    // One query for the list screen. A filter that is null is simply skipped.
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId"
            + " AND (:from IS NULL OR t.txnDate >= :from)"
            + " AND (:to IS NULL OR t.txnDate <= :to)"
            + " AND (:type IS NULL OR t.type = :type)"
            + " AND (:categoryId IS NULL OR t.categoryId = :categoryId)"
            + " AND (:accountId IS NULL OR t.accountId = :accountId)")
    Page<Transaction> search(Long userId, LocalDate from, LocalDate to, TransactionType type, Long categoryId,
            Long accountId, Pageable pageable);

    // Total of one type in a date range. The database adds the DECIMAL values, so no rounding errors.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.userId = :userId"
            + " AND t.type = :type AND t.txnDate >= :from AND t.txnDate <= :to")
    BigDecimal sumAmount(Long userId, TransactionType type, LocalDate from, LocalDate to);

    // Money spent in one category in a date range (used by budgets, T17)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.userId = :userId"
            + " AND t.categoryId = :categoryId AND t.type = lk.finora.api.transaction.TransactionType.EXPENSE"
            + " AND t.txnDate >= :from AND t.txnDate <= :to")
    BigDecimal sumExpenseForCategory(Long userId, Long categoryId, LocalDate from, LocalDate to);
}
