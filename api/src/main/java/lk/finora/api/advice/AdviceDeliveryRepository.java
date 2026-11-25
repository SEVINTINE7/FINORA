package lk.finora.api.advice;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.common.error.ApiException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AdviceDeliveryRepository {
    public record Delivered(String month, Long id, String source, boolean verified, List<AdviceItem> items,
                            Integer rating) {}
    private record StoredItem(String actionId, String text, List<StoredFact> numbers) {}
    private record StoredFact(String factKey, String value, String unit, String period) {}
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final FinanceWriteLock lock;

    public AdviceDeliveryRepository(JdbcTemplate jdbc, JsonMapper json, FinanceWriteLock lock) {
        this.jdbc = jdbc;
        this.json = json;
        this.lock = lock;
    }

    public Optional<Delivered> find(long owner, String month) { return find(owner, month, null); }

    public boolean ownsGoal(long owner, long goalId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM goals WHERE id = ? AND user_id = ?", Integer.class, goalId, owner) == 1;
    }

    private static long key(Long goalId) { return goalId == null ? 0L : goalId; }

    public Optional<Delivered> find(long owner, String month, Long goalId) {
        return jdbc.query("""
                SELECT a.id, a.source, a.verified, a.body, r.rating
                FROM advice a LEFT JOIN advice_ratings r ON r.advice_id = a.id AND r.user_id = a.user_id
                WHERE a.user_id = ? AND a.month = ? AND a.focus_key = ? AND a.monthly_delivery = TRUE
                """, (row, n) -> new Delivered(month, row.getLong("id"), row.getString("source"),
                row.getBoolean("verified"), List.of(json.readValue(row.getString("body"), AdviceItem[].class)),
                row.getObject("rating", Integer.class)), owner, month, key(goalId)).stream().findFirst();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean claimGeneration(long owner, String month, String token) { return claimGeneration(owner, month, null, token); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean claimGeneration(long owner, String month, Long goalId, String token) {
        lock.acquire(owner);
        if (find(owner, month, goalId).isPresent()) return false;
        jdbc.update("""
                DELETE FROM advice_generation_leases
                WHERE user_id = ? AND month = ? AND focus_key = ? AND expires_at <= UTC_TIMESTAMP(6)
                """, owner, month, key(goalId));
        return jdbc.update("""
                INSERT INTO advice_generation_leases (user_id, month, focus_key, lease_token, expires_at)
                SELECT ?, ?, ?, ?, UTC_TIMESTAMP(6) + INTERVAL 120 SECOND
                WHERE NOT EXISTS (
                    SELECT 1 FROM advice_generation_leases WHERE user_id = ? AND month = ? AND focus_key = ?
                )
                """, owner, month, key(goalId), token, owner, month, key(goalId)) == 1;
    }

    public void releaseGeneration(long owner, String month, String token) { releaseGeneration(owner, month, null, token); }

    public void releaseGeneration(long owner, String month, Long goalId, String token) {
        jdbc.update("""
                DELETE FROM advice_generation_leases WHERE user_id = ? AND month = ? AND focus_key = ? AND lease_token = ?
                """, owner, month, key(goalId), token);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Delivered publish(long owner, String month, String token, AdvicePipelineService.Delivery candidate) {
        return publish(owner, month, null, token, candidate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Delivered publish(long owner, String month, Long goalId, String token, AdvicePipelineService.Delivery candidate) {
        lock.acquire(owner);
        return find(owner, month, goalId).orElseGet(() -> {
            if (jdbc.update("""
                    DELETE FROM advice_generation_leases
                    WHERE user_id = ? AND month = ? AND focus_key = ? AND lease_token = ? AND expires_at > UTC_TIMESTAMP(6)
                    """, owner, month, key(goalId), token) != 1) {
                throw AdviceDeliveryService.generationInProgress();
            }
            // MySQL JSON can coerce fractional JSON numbers to binary floating point.
            var stored = candidate.advice().stream().map(item -> new StoredItem(item.actionId(), item.text(),
                    item.numbers().stream().map(fact -> new StoredFact(fact.factKey().name(),
                            fact.value().toPlainString(), fact.unit(), fact.period())).toList())).toList();
            jdbc.update("""
                    INSERT INTO advice (user_id, month, source, mode, body, verified, monthly_delivery, focus_goal_id)
                    VALUES (?, ?, ?, 'V1', ?, ?, TRUE, ?)
                    """, owner, month, candidate.source(), json.writeValueAsString(stored), candidate.verified(), goalId);
            return find(owner, month, goalId).orElseThrow();
        });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void rate(long owner, long id, int value) {
        lock.acquire(owner);
        try {
            int added = jdbc.update("""
                    INSERT INTO advice_ratings (advice_id, user_id, rating)
                    SELECT id, user_id, ? FROM advice
                    WHERE id = ? AND user_id = ? AND monthly_delivery = TRUE
                    """, value, id, owner);
            if (added == 0) throw ApiException.notFound("Advice not found");
        } catch (DuplicateKeyException e) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "Advice already rated");
        }
    }
}
