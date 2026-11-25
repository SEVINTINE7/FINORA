package lk.finora.api.staff;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Optional first-admin bootstrap. A shared row serializes concurrent application starts. */
@Component
class FirstAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder encoder;
    private final Environment environment;

    FirstAdminBootstrap(JdbcTemplate jdbc, TransactionTemplate transaction, PasswordEncoder encoder,
            Environment environment) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override public void run(ApplicationArguments arguments) {
        String email = environment.getProperty("FINORA_BOOTSTRAP_ADMIN_EMAIL");
        String password = environment.getProperty("FINORA_BOOTSTRAP_ADMIN_PASSWORD");
        if (email == null && password == null) return;
        initialize(email, password);
    }

    void initialize(String email, String password) {
        if (email == null || email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+")
                || password == null || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.isBlank()) {
            throw new IllegalStateException("Invalid first-admin bootstrap configuration");
        }
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM admin_bootstrap_lock WHERE id = 1 FOR UPDATE", Integer.class);
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN')", Boolean.class))) return;
            jdbc.update("""
                    INSERT INTO users (email, full_name, password_hash, role)
                    VALUES (?, 'FINORA administrator', ?, 'ADMIN')
                    """, email.toLowerCase(Locale.ROOT), encoder.encode(password));
        });
    }
}
