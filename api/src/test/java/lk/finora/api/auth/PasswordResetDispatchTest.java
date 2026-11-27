package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PasswordResetDispatchTest extends IntegrationTest {
    private static final LinkedBlockingQueue<SimpleMailMessage> delivered = new LinkedBlockingQueue<>();
    @TestBean PasswordResetMail passwordResetMail;
    @Autowired JdbcTemplate jdbc;

    static PasswordResetMail passwordResetMail() {
        var transport = new JavaMailSenderImpl() {
            @Override public void send(SimpleMailMessage message) {
                delivered.add(message);
            }
        };
        return new PasswordResetMail("smtp.example.test", 587, "test", "synthetic",
                "reset@example.test", Runnable::run, transport);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void httpRequestDispatchesAfterCommitAndDeliveredCodeResetsTheAccount() throws Exception {
        var user = createUser(Role.USER);
        try {
            mockMvc.perform(post("/api/v1/auth/password/forgot").contentType("application/json")
                    .content(json(Map.of("email", user.getEmail())))).andExpect(status().isAccepted());
            var message = delivered.poll(15, TimeUnit.SECONDS);
            assertThat(message != null).isTrue();
            assertThat(message.getTo()).containsExactly(user.getEmail());
            var code = Pattern.compile("Code: ([0-9]{6})\\nExpires in 15 minutes\\.").matcher(message.getText());
            assertThat(code.matches()).isTrue();
            mockMvc.perform(post("/api/v1/auth/password/reset").contentType("application/json").content(json(
                    Map.of("email", user.getEmail(), "code", code.group(1), "newPassword", "SyntheticReset2026"))))
                    .andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes WHERE user_id=?",
                    Integer.class, user.getId())).isZero();
        } finally {
            jdbc.update("DELETE FROM users WHERE id=?", user.getId());
        }
    }
}
