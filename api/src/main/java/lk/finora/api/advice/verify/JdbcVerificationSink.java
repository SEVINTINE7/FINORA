package lk.finora.api.advice.verify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVerificationSink implements VerificationSink {
    private final JdbcTemplate jdbc;
    public JdbcVerificationSink(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void record(Long userId, String month, String provider, boolean verified, AdviceVerifier.Reason reason) {
        if (!java.util.Set.of("GROQ", "GEMINI").contains(provider)) throw new IllegalArgumentException("Unknown provider");
        jdbc.update("""
                INSERT INTO advice_verification_events(user_id,month,provider,verified,reason)
                SELECT id,?,?,?,? FROM users WHERE id=? AND role='USER' FOR UPDATE
                """, month, provider, verified, reason.name(), userId);
    }
}
