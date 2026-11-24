package lk.finora.api.smsformat;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SmsFormatRepository {
    private final JdbcTemplate jdbc;

    SmsFormatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<String> activeNsbV1() {
        return jdbc.queryForList(
                "SELECT pattern FROM sms_formats WHERE bank_code = ? AND version = ? AND active = TRUE",
                String.class, "NSB", 1);
    }
}
