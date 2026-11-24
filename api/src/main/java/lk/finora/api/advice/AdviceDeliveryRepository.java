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

    public Optional<Delivered> find(long owner, String month) {
        return jdbc.query("""
                SELECT a.id, a.source, a.verified, a.body, r.rating
                FROM advice a LEFT JOIN advice_ratings r ON r.advice_id = a.id AND r.user_id = a.user_id
                WHERE a.user_id = ? AND a.month = ? AND a.monthly_delivery = TRUE
                """, (row, n) -> new Delivered(month, row.getLong("id"), row.getString("source"),
                row.getBoolean("verified"), List.of(json.readValue(row.getString("body"), AdviceItem[].class)),
                row.getObject("rating", Integer.class)), owner, month).stream().findFirst();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Delivered publish(long owner, String month, AdvicePipelineService.Delivery candidate) {
        lock.acquire(owner);
        return find(owner, month).orElseGet(() -> {
            // MySQL JSON can coerce fractional JSON numbers to binary floating point.
            var stored = candidate.advice().stream().map(item -> new StoredItem(item.actionId(), item.text(),
                    item.numbers().stream().map(fact -> new StoredFact(fact.factKey().name(),
                            fact.value().toPlainString(), fact.unit(), fact.period())).toList())).toList();
            jdbc.update("""
                    INSERT INTO advice (user_id, month, source, mode, body, verified, monthly_delivery)
                    VALUES (?, ?, ?, 'V1', ?, ?, TRUE)
                    """, owner, month, candidate.source(), json.writeValueAsString(stored), candidate.verified());
            return find(owner, month).orElseThrow();
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
