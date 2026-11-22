package lk.finora.api.score;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreInputRepository extends JpaRepository<ScoreInput, Long> {
    Optional<ScoreInput> findByUserIdAndMonth(Long userId, String month);
}
