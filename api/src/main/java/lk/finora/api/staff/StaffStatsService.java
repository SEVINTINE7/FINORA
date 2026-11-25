package lk.finora.api.staff;

import lk.finora.api.staff.dto.StaffStatsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class StaffStatsService {
    private final JdbcTemplate jdbc;

    StaffStatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    StaffStatsResponse read(long staffId) {
        return jdbc.queryForObject("""
                SELECT COUNT(CASE WHEN staff_id = ? THEN 1 END) AS mine,
                       COUNT(CASE WHEN staff_id IS NULL THEN 1 END) AS unassigned
                FROM support_tickets
                WHERE status IN ('OPEN', 'IN_PROGRESS')
                """, (row, n) -> new StaffStatsResponse(row.getLong("mine"), row.getLong("unassigned")), staffId);
    }
}
