package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PasswordResetMailTest {
    @Test void requiresVerifiedTlsAndCompleteConfiguration() {
        for (int port : new int[]{465, 587}) {
            var sender = new JavaMailSenderImpl();
            var mail = new PasswordResetMail("smtp.example.test", port, "test", "synthetic", "reset@example.test",
                    Runnable::run, sender);
            assertThat(mail.configured()).isTrue();
            var props = sender.getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
            assertThat(props.getProperty(port == 465 ? "mail.smtp.ssl.enable" : "mail.smtp.starttls.required"))
                    .isEqualTo("true");
        }
        assertThat(new PasswordResetMail("", 587, "", "", "", Runnable::run, new JavaMailSenderImpl())
                .configured()).isFalse();
    }

    @Test void deliversOnlyAfterCommitAndDiscardsRolledBackMessages() {
        var sender = spy(new JavaMailSenderImpl());
        doNothing().when(sender).send(any(SimpleMailMessage.class));
        var mail = new PasswordResetMail("smtp.example.test", 587, "test", "synthetic", "reset@example.test",
                Runnable::run, sender);
        for (boolean commit : new boolean[]{false, true}) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                mail.afterCommit("person@example.test", "SI", "012345");
                var callback = TransactionSynchronizationManager.getSynchronizations().get(0);
                verify(sender, never()).send(any(SimpleMailMessage.class));
                if (commit) callback.afterCommit();
                callback.afterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED
                        : TransactionSynchronization.STATUS_ROLLED_BACK);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().getTo()).containsExactly("person@example.test");
        assertThat(captured.getValue().getFrom()).isEqualTo("reset@example.test");
        assertThat(captured.getValue().getText()).contains("012345", "15");
    }
}
