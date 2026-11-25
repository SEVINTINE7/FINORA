package lk.finora.api.support;

import java.util.List;
import java.util.Set;
import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
class SmsDiagnosticService {
    private static final Set<String> ERRORS = Set.of("FORMAT_UNRECOGNIZED", "FORMAT_AMBIGUOUS",
            "REQUIRED_FIELD_INVALID", "FORMAT_UNAVAILABLE");
    private final JdbcTemplate jdbc;

    SmsDiagnosticService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void submit(long userId, JsonNode payload) {
        if (!payload.isObject() || payload.size() != 3 || !payload.has("bankCode")
                || !payload.has("formatVersion") || !payload.has("errorCode")) {
            throw invalid();
        }
        JsonNode bank = payload.get("bankCode");
        JsonNode version = payload.get("formatVersion");
        JsonNode error = payload.get("errorCode");
        // D35 approves only the NSB v1 bundle. Unknown values get internal sentinel keys for deduplication.
        if ((!bank.isNull() && (!bank.isString() || !"NSB".equals(bank.asString())))
                || (!version.isNull() && (bank.isNull() || !version.isIntegralNumber()
                        || !version.canConvertToInt() || version.asInt() != 1))
                || !error.isString() || !ERRORS.contains(error.asString())) {
            throw invalid();
        }
        jdbc.update("""
                INSERT INTO sms_diagnostic_reports (user_id, bank_code, format_version, error_code, report_day)
                VALUES (?, ?, ?, ?, UTC_DATE())
                ON DUPLICATE KEY UPDATE report_day = report_day
                """, userId, bank.isNull() ? "" : bank.asString(), version.isNull() ? 0 : 1, error.asString());
    }

    List<Group> groups() {
        return jdbc.query("""
                SELECT bank_code, error_code, format_version, COUNT(*) AS reports
                FROM sms_diagnostic_reports
                GROUP BY bank_code, error_code, format_version
                ORDER BY bank_code, error_code, format_version
                """, (row, n) -> {
                    String bank = row.getString("bank_code");
                    int version = row.getInt("format_version");
                    return new Group(bank.isEmpty() ? null : bank, row.getString("error_code"),
                            version == 0 ? null : version, row.getLong("reports"));
                });
    }

    void flag(JsonNode payload) {
        JsonNode bank = payload.path("bankCode");
        JsonNode error = payload.path("errorCode");
        if (!payload.isObject() || payload.size() != 2 || (!bank.isNull() && !"NSB".equals(bank.asString("")))
                || !error.isString() || !ERRORS.contains(error.asString())) {
            throw invalid();
        }
        int inserted = jdbc.update("""
                INSERT INTO sms_diagnostic_escalations (bank_code, error_code)
                SELECT bank_code, error_code FROM sms_diagnostic_reports
                WHERE bank_code = ? AND error_code = ?
                GROUP BY bank_code, error_code
                ON DUPLICATE KEY UPDATE id = sms_diagnostic_escalations.id
                """, bank.isNull() ? "" : bank.asString(), error.asString());
        if (inserted == 0 && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM sms_diagnostic_reports WHERE bank_code = ? AND error_code = ?)
                """, Boolean.class, bank.isNull() ? "" : bank.asString(), error.asString()))) {
            throw ApiException.notFound("SMS diagnostic group unavailable");
        }
    }

    List<Escalation> escalations() {
        return jdbc.query("""
                SELECT id, bank_code, error_code, status, created_at
                FROM sms_diagnostic_escalations
                WHERE status != 'CLOSED'
                ORDER BY id DESC
                LIMIT 100
                """, (row, n) -> {
                    String bank = row.getString("bank_code");
                    return new Escalation(row.getLong("id"), bank.isEmpty() ? null : bank,
                            row.getString("error_code"), row.getString("status"),
                            row.getObject("created_at", java.time.LocalDateTime.class)
                                    .toInstant(java.time.ZoneOffset.UTC));
                });
    }

    void advance(long id, JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("status").isString()) throw invalid();
        String status = payload.path("status").asString();
        String previous = switch (status) {
            case "ACKNOWLEDGED" -> "OPEN";
            case "CLOSED" -> "ACKNOWLEDGED";
            default -> throw invalid();
        };
        if (jdbc.update("UPDATE sms_diagnostic_escalations SET status = ? WHERE id = ? AND status = ?",
                status, id, previous) != 1) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "SMS diagnostic escalation changed or is unavailable");
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("Invalid SMS diagnostic metadata");
    }

    record Group(String bankCode, String errorCode, Integer formatVersion, long reports) {}
    record Escalation(long id, String bankCode, String errorCode, String status, java.time.Instant createdAt) {}
}
