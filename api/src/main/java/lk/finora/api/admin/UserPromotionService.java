package lk.finora.api.admin;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.user.FinancialDataDeletion;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserPromotionService {
    private final JdbcTemplate jdbc;
    private final FinancialDataDeletion financialData;

    UserPromotionService(JdbcTemplate jdbc, FinancialDataDeletion financialData) {
        this.jdbc = jdbc;
        this.financialData = financialData;
    }

    @Transactional
    public StaffSummary promote(long actorId, long targetId, String confirm) {
        if (!"DELETE_FINANCIAL_DATA".equals(confirm)) {
            throw ApiException.badRequest("Explicit financial data deletion confirmation is required");
        }
        if (actorId == targetId) throw conflict();

        var users = jdbc.query("""
                SELECT id, email, full_name, role, status FROM users WHERE id IN (?, ?)
                ORDER BY id FOR UPDATE
                """, (row, index) -> new Identity(row.getLong("id"), row.getString("email"),
                row.getString("full_name"), row.getString("role"), row.getString("status")),
                actorId, targetId);
        boolean authorized = users.stream().anyMatch(user -> user.id() == actorId
                && user.role().equals("ADMIN") && user.status().equals("ACTIVE"));
        if (!authorized) throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN, "Forbidden");
        var target = users.stream().filter(user -> user.id() == targetId).findFirst()
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!target.role().equals("USER") || !target.status().equals("ACTIVE")) throw conflict();

        financialData.erase(targetId);
        jdbc.update("UPDATE users SET role='STAFF', staff_session_generation=staff_session_generation+1 WHERE id=?",
                targetId);
        jdbc.update("""
                UPDATE refresh_tokens SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL
                """, targetId);
        jdbc.update("DELETE FROM consent_recovery_grants WHERE user_id=?", targetId);
        jdbc.update("DELETE FROM password_reset_codes WHERE user_id=?", targetId);
        jdbc.update("""
                INSERT INTO audit_events (actor_id, action, target_type, target_id)
                VALUES (?, 'USER_PROMOTED_TO_STAFF', 'STAFF', ?)
                """, actorId, targetId);
        return new StaffSummary(targetId, target.email(), target.fullName(), "ACTIVE", false);
    }

    private static ApiException conflict() {
        return new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT, "Only another active user can be promoted");
    }

    private record Identity(long id, String email, String fullName, String role, String status) {}
    record StaffSummary(long id, String email, String fullName, String status, boolean pending) {}
}
