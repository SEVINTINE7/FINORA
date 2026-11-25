package lk.finora.api.support;

import java.sql.Statement;
import java.util.List;
import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupportTicketService {
    private final JdbcTemplate jdbc;

    SupportTicketService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    long create(long user, String subject, String body) {
        return create(user, subject, body, null);
    }

    long create(long user, String subject, String body, String errorCode) {
        SupportCopy option = SupportCopy.ticket(subject, body);
        String expected = switch (option) {
            case SIGN_IN -> "SIGN_IN_FAILED";
            case NAVIGATION -> "NAVIGATION_HELP";
            case SMS_PERMISSION -> "SMS_PERMISSION_DENIED";
            case LANGUAGE -> "LANGUAGE_UPDATE_FAILED";
        };
        if (errorCode != null && !expected.equals(errorCode)) {
            throw ApiException.badRequest("Unsupported help error code");
        }
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var insert = connection.prepareStatement(
                    "INSERT INTO support_tickets (user_id, subject, body, error_code) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            insert.setLong(1, user);
            insert.setString(2, option.subject);
            insert.setString(3, option.userBody);
            insert.setString(4, errorCode);
            return insert;
        }, key);
        return java.util.Objects.requireNonNull(key.getKey()).longValue();
    }

    List<Ticket> own(long user) {
        return read("SELECT id, subject, body, status, error_code FROM support_tickets WHERE user_id = ? ORDER BY id DESC LIMIT 100", user);
    }

    List<Ticket> staff(long staff) {
        return read("""
                SELECT id, IF(staff_id IS NULL, NULL, subject) AS subject,
                       IF(staff_id IS NULL, NULL, body) AS body,
                       IF(staff_id IS NULL, NULL, error_code) AS error_code, status
                FROM support_tickets
                WHERE (staff_id IS NULL AND status = 'OPEN') OR staff_id = ?
                ORDER BY id DESC LIMIT 100
                """, staff);
    }

    private List<Ticket> read(String sql, Object... args) {
        return jdbc.query(sql, (row, index) -> new Ticket(row.getLong("id"), row.getString("subject"),
                row.getString("body"), row.getString("status"), row.getString("error_code")), args);
    }

    void claim(long staff, long ticket) {
        if (jdbc.update("""
                UPDATE support_tickets SET staff_id = ?, status = 'IN_PROGRESS'
                WHERE id = ? AND staff_id IS NULL AND status = 'OPEN'
                """, staff, ticket) != 1) throw missing();
    }

    void close(long staff, long ticket) {
        if (jdbc.update("""
                UPDATE support_tickets SET status = 'CLOSED'
                WHERE id = ? AND staff_id = ? AND status = 'IN_PROGRESS'
                """, ticket, staff) != 1) throw missing();
    }

    @Transactional
    public void reply(long staff, long ticket, long revision, String language) {
        var assigned = jdbc.queryForList("""
                SELECT id FROM support_tickets
                WHERE id = ? AND staff_id = ? AND status = 'IN_PROGRESS' FOR UPDATE
                """, Long.class, ticket, staff);
        if (assigned.isEmpty()) throw missing();
        if (!"EN".equals(language) && !"SI".equals(language)) {
            throw ApiException.badRequest("Unsupported reply language");
        }
        var replies = jdbc.query("""
                SELECT english, sinhala FROM support_reply_revisions WHERE id = ? AND state = 'ACTIVE' FOR UPDATE
                """, (row, index) -> row.getString("EN".equals(language) ? "english" : "sinhala"), revision);
        if (replies.isEmpty()) throw missing();
        var body = replies.get(0);
        jdbc.update("INSERT INTO ticket_replies (ticket_id, author_id, body) VALUES (?, ?, ?)", ticket, staff, body);
    }

    List<Reply> replies(long owner, long ticket) {
        if (read("SELECT id, subject, body, status, error_code FROM support_tickets WHERE id = ? AND user_id = ?",
                ticket, owner).isEmpty()) throw missing();
        return jdbc.query("""
                SELECT r.body FROM ticket_replies r JOIN support_tickets t ON t.id = r.ticket_id
                WHERE t.id = ? AND t.user_id = ? ORDER BY r.id LIMIT 100
                """, (row, n) -> new Reply(row.getString("body")), ticket, owner);
    }

    private static ApiException missing() { return ApiException.notFound("Support item unavailable"); }

    void draft(long staff, String topic) {
        var copy = SupportCopy.topic(topic);
        jdbc.update("INSERT INTO support_reply_revisions (topic, english, sinhala, draft_by) VALUES (?, ?, ?, ?)",
                copy.name(), copy.replyEn, copy.replySi, staff);
    }

    List<Revision> catalog() {
        return jdbc.query("""
                SELECT id, topic, state, english, sinhala FROM support_reply_revisions ORDER BY id DESC LIMIT 100
                """, (row, n) -> new Revision(row.getLong("id"), row.getString("topic"),
                        row.getString("state"), row.getString("english"), row.getString("sinhala")));
    }

    void review(long staff, long revision) {
        try {
            if (jdbc.update("""
                    UPDATE support_reply_revisions SET state = 'ACTIVE', reviewed_by = ?
                    WHERE id = ? AND state = 'DRAFT' AND draft_by <> ?
                    """, staff, revision, staff) != 1) throw missing();
        } catch (org.springframework.dao.DuplicateKeyException alreadyActive) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "Retire the active revision first");
        }
    }

    void retire(long revision) {
        if (jdbc.update("UPDATE support_reply_revisions SET state = 'RETIRED' WHERE id = ? AND state <> 'RETIRED'",
                revision) != 1) throw missing();
    }

    record Ticket(long id, String subject, String body, String status,
            @com.fasterxml.jackson.annotation.JsonProperty("error_code") String errorCode) {}
    record Reply(String body) {}
    record Revision(long id, String topic, String state, String english, String sinhala) {}
}
