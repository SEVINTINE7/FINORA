package lk.finora.api.notification;

import java.util.List;
import lk.finora.api.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push tokens per user (T34.1, table device_tokens from V5). A token belongs to one user: when a phone
 * signs in to another account the token moves to that account. Each user keeps at most
 * {@link #MAX_DEVICES} tokens; the oldest are dropped.
 */
@Service
public class DeviceService {

    static final int MAX_DEVICES = 10;

    private final JdbcTemplate jdbc;

    public DeviceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void register(Long userId, String token) {
        Long owner = jdbc.query("SELECT user_id FROM device_tokens WHERE token = ? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, token);
        if (userId.equals(owner)) {
            return;
        }
        if (owner != null) {
            jdbc.update("DELETE FROM device_tokens WHERE token = ?", token);
        }
        jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", userId, token);
        List<Long> extra = jdbc.queryForList("""
                SELECT id FROM device_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1000 OFFSET ?
                """, Long.class, userId, MAX_DEVICES);
        for (Long id : extra) {
            jdbc.update("DELETE FROM device_tokens WHERE id = ?", id);
        }
    }

    /** Another user's token (or an unknown one) gives 404, never 403. */
    @Transactional
    public void remove(Long userId, String token) {
        if (jdbc.update("DELETE FROM device_tokens WHERE token = ? AND user_id = ?", token, userId) == 0) {
            throw ApiException.notFound("Device not found");
        }
    }

    @Transactional(readOnly = true)
    public List<String> tokens(Long userId) {
        return jdbc.queryForList("SELECT token FROM device_tokens WHERE user_id = ? ORDER BY id", String.class, userId);
    }

    /** Drops tokens that FCM reported as no longer valid. */
    @Transactional
    public void forget(List<String> tokens) {
        for (String token : tokens) {
            jdbc.update("DELETE FROM device_tokens WHERE token = ?", token);
        }
    }
}
