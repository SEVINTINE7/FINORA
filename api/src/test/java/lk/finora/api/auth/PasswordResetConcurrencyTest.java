package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetConcurrencyTest extends IntegrationTest {
    @Autowired PasswordResetService resets;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PasswordResetMail mail;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedConcurrentRequestsRespectTheLimitAndOnlyOneResetCanWin() throws Exception {
        var owner = createUser(Role.USER);
        try {
            when(mail.configured()).thenReturn(true);
            parallel(4, () -> { resets.issue(owner.getEmail()); return true; });
            var codes = ArgumentCaptor.forClass(String.class);
            verify(mail, times(3)).afterCommit(eq(owner.getEmail()), eq("EN"), codes.capture());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_issues WHERE user_id=?",
                    Integer.class, owner.getId())).isEqualTo(3);
            String hash = jdbc.queryForObject("SELECT code_hash FROM password_reset_codes WHERE user_id=?",
                    String.class, owner.getId());
            String code = codes.getAllValues().stream().filter(value -> encoder.matches(value, hash))
                    .findFirst().orElseThrow();
            assertThat(parallel(2, () -> resets.reset(owner.getEmail(), code, "NewSynthetic2026")))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND action='PASSWORD_RESET'",
                    Integer.class, owner.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes WHERE user_id=?",
                    Integer.class, owner.getId())).isZero();
        } finally {
            jdbc.update("DELETE FROM users WHERE id=?", owner.getId());
        }
    }

    private static List<Boolean> parallel(int count, Callable<Boolean> action) throws Exception {
        var workers = Executors.newFixedThreadPool(count);
        var start = new CyclicBarrier(count);
        try {
            List<Future<Boolean>> calls = IntStream.range(0, count).mapToObj(i -> workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return action.call();
            })).toList();
            var results = new java.util.ArrayList<Boolean>();
            for (var call : calls) results.add(call.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            workers.shutdownNow();
        }
    }
}
