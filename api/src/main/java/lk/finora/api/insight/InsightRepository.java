package lk.finora.api.insight;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InsightRepository extends JpaRepository<Insight, Long> {

    List<Insight> findByUserIdAndMonthOrderByIdAsc(Long userId, String month);

    // One DELETE statement that runs straight away (a derived delete would wait until after the new inserts)
    @Modifying
    @Query("DELETE FROM Insight i WHERE i.userId = :userId AND i.month = :month")
    void deleteByUserIdAndMonth(Long userId, String month);
}
