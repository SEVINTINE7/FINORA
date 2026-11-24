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

    List<String> activeNsb() {
        return jdbc.queryForList(
                "SELECT pattern FROM sms_formats WHERE bank_code = 'NSB' AND active = TRUE AND published = TRUE",
                String.class);
    }

    List<String> lockNsbV1() {
        return jdbc.queryForList("""
                SELECT pattern FROM sms_formats WHERE bank_code = 'NSB' AND version = 1 FOR UPDATE
                """, String.class);
    }

    void deactivate(int revision) {
        jdbc.update("UPDATE sms_formats SET active = FALSE WHERE bank_code = 'NSB' AND version = ?", revision);
    }

    void draftNsb(int revision, String definition) {
        jdbc.update("INSERT INTO sms_formats (bank_code, version, pattern) VALUES ('NSB', ?, ?)", revision, definition);
    }

    boolean deleteDraft(int revision) {
        return jdbc.update("""
                DELETE FROM sms_formats WHERE bank_code = 'NSB' AND version = ?
                  AND published = FALSE AND active = FALSE
                """, revision) == 1;
    }

    List<String> revision(int revision, boolean published) {
        return jdbc.queryForList("""
                SELECT pattern FROM sms_formats WHERE bank_code = 'NSB' AND version = ? AND published = ?
                """, String.class, revision, published);
    }

    void editDraft(int revision, String definition) {
        jdbc.update("""
                UPDATE sms_formats SET pattern = ?, validated_definition = NULL, validated_fixtures = NULL
                WHERE bank_code = 'NSB' AND version = ? AND published = FALSE AND active = FALSE
                """, definition, revision);
    }

    void recordValidation(int revision, String definitionDigest, String fixtureRevision) {
        jdbc.update("""
                UPDATE sms_formats SET validated_definition = ?, validated_fixtures = ?
                WHERE bank_code = 'NSB' AND version = ?
                """, definitionDigest, fixtureRevision, revision);
    }

    boolean hasValidation(int revision, String definitionDigest, String fixtureRevision) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM sms_formats WHERE bank_code = 'NSB' AND version = ?
                AND validated_definition = ? AND validated_fixtures = ?)
                """, Boolean.class, revision, definitionDigest, fixtureRevision));
    }

    void publish(int revision) {
        jdbc.update("UPDATE sms_formats SET published = TRUE WHERE bank_code = 'NSB' AND version = ?", revision);
    }

    void activate(int revision) {
        jdbc.update("UPDATE sms_formats SET active = FALSE WHERE bank_code = 'NSB' AND active = TRUE");
        jdbc.update("UPDATE sms_formats SET active = TRUE WHERE bank_code = 'NSB' AND version = ?", revision);
    }

    List<SmsFormatService.Revision> revisions() {
        return jdbc.query("""
                SELECT version, published, active FROM sms_formats WHERE bank_code = 'NSB' ORDER BY version DESC LIMIT 100
                """, (row, n) -> new SmsFormatService.Revision(row.getInt("version"),
                        row.getBoolean("published"), row.getBoolean("active")));
    }
}
