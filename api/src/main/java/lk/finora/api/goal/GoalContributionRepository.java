package lk.finora.api.goal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    List<GoalContribution> findByGoalIdOrderByContribDateDescIdDesc(Long goalId);

    // The goal id is checked too, so a contribution of another goal is "not found"
    Optional<GoalContribution> findByIdAndGoalId(Long id, Long goalId);

    // Saved so far for one goal, summed in MySQL (DECIMAL, exact)
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContribution c WHERE c.goalId = :goalId")
    BigDecimal sumAmount(Long goalId);
}
