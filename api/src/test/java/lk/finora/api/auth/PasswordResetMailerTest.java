package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class PasswordResetMailerTest {
    @Test void englishAndSinhalaPlainTextContainOnlyCodeAndExpiry() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var mailer = new PasswordResetMailer(sender, "smtp.example.test", "no-reply@example.test");
        try {
            mailer.send("one@example.test", "EN", "012345");
            mailer.send("two@example.test", "SI", "987654");
            var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender, timeout(5000).times(2)).send(captured.capture());
            var messages = captured.getAllValues();
            assertThat(messages.get(0).getText()).isEqualTo("Password reset code: 012345\nExpires 15 minutes after the request.");
            assertThat(messages.get(1).getText()).contains("987654", "15", "\u0db8\u0dd4\u0dbb\u0db4\u0daf\u0dba");
            assertThat(messages.get(1).getText()).doesNotContain("two@example.test");
            assertThat(messages.get(0).getFrom()).isEqualTo("no-reply@example.test");
            assertThat(messages.get(0).getTo()).containsExactly("one@example.test");
            assertThat(messages.get(1).getTo()).containsExactly("two@example.test");
        } finally { mailer.close(); }
    }

    @Test void missingConfigurationNeverSendsMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var mailer = new PasswordResetMailer(sender, "", "no-reply@example.test");
        try { mailer.send("one@example.test", "EN", "012345"); }
        finally { mailer.close(); }
        verifyNoInteractions(sender);
    }

    @Test void smtpTransportRequiresTlsWithBoundedTimeouts() {
        var config = new PasswordResetMailConfig();
        var starttls = (JavaMailSenderImpl) config.passwordResetMailSender("smtp.example.test", 587, "synthetic", "test-only");
        assertThat(starttls.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true").containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.debug", "false");
        var tls = (JavaMailSenderImpl) config.passwordResetMailSender("smtp.example.test", 465, "synthetic", "test-only");
        assertThat(tls.getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
    }
}
