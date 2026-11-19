package lk.finora.api.budget;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndMonthOrderById(Long userId, String month);

    // Ownership check: only finds the budget if it belongs to this user
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    // Only one budget per category per month
    boolean existsByUserIdAndCategoryIdAndMonth(Long userId, Long categoryId, String month);

    // Same check for an update, skipping the budget being edited
    boolean existsByUserIdAndCategoryIdAndMonthAndIdNot(Long userId, Long categoryId, String month, Long id);
}
