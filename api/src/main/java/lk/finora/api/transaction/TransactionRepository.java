package lk.finora.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Ownership check: only finds the record if it belongs to this user
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    // All of a user's records in a date range, both ends included (spending metrics, T21)
    List<Transaction> findByUserIdAndTxnDateBetween(Long userId, LocalDate from, LocalDate to);

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

    // Expenses per category in a date range (dashboard pie, T20). Each row is [categoryId (can be null), total].
    @Query("SELECT t.categoryId, SUM(t.amount) FROM Transaction t WHERE t.userId = :userId"
            + " AND t.type = lk.finora.api.transaction.TransactionType.EXPENSE"
            + " AND t.txnDate >= :from AND t.txnDate <= :to"
            + " GROUP BY t.categoryId ORDER BY SUM(t.amount) DESC")
    List<Object[]> sumExpenseByCategory(Long userId, LocalDate from, LocalDate to);

    // Which of these dedup hashes the user already has (CSV import duplicate check, T12)
    @Query("SELECT t.dedupHash FROM Transaction t WHERE t.userId = :userId AND t.dedupHash IN :hashes")
    List<String> findExistingDedupHashes(Long userId, Collection<String> hashes);
}
