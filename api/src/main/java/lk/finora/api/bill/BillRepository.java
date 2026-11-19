package lk.finora.api.bill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByUserIdAndActiveTrueOrderByDueDayAscIdAsc(Long userId);

    // Ownership check: only finds the bill if it belongs to this user
    Optional<Bill> findByIdAndUserId(Long id, Long userId);

    // Total of all the user's bills for one month (every bill comes once a month)
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Bill b WHERE b.userId = :userId AND b.active = true")
    BigDecimal sumActiveAmount(Long userId);
}
