package lk.finora.api.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderById(Long userId);

    // Ownership check: only finds the account if it belongs to this user
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    // For updates: does ANOTHER account of this user already have the name?
    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    // Called after the owner's finance write lock has been acquired.
    @Query(value = "SELECT COUNT(*) FROM transactions WHERE account_id = :accountId", nativeQuery = true)
    long countTransactions(Long accountId);
}
