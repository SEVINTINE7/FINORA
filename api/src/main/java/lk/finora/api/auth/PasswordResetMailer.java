package lk.finora.api.auth;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text EN/SI delivery. No SMTP exception, recipient, code or password is logged. */
@Component
class PasswordResetMailer {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);
    private final JavaMailSender sender;
    private final String from;
    private final boolean configured;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "finora-reset-mail");
                thread.setDaemon(true);
                return thread;
            });

    PasswordResetMailer(JavaMailSender sender, @Value("${FINORA_MAIL_HOST:}") String host,
            @Value("${FINORA_MAIL_FROM:}") String from) {
        this.sender = sender;
        this.from = from;
        this.configured = !host.isBlank() && !from.isBlank();
    }

    void send(String email, String language, String code) {
        if (!configured) return;
        try {
            executor.execute(() -> {
                try {
                    sender.send(message(email, language, code));
                } catch (RuntimeException failed) {
                    // Mail exceptions can contain addresses and message content. Never log the exception.
                    log.warn("Password reset mail delivery failed");
                }
            });
        } catch (RejectedExecutionException busy) {
            log.warn("Password reset mail queue unavailable");
        }
    }

    private SimpleMailMessage message(String email, String language, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        if ("SI".equals(language)) {
            message.setSubject("FINORA - \u0db8\u0dd4\u0dbb\u0db4\u0daf\u0dba \u0dba\u0dc5\u0dd2 \u0dc3\u0d9a\u0dc3\u0db1\u0dca\u0db1");
            message.setText("\u0db8\u0dd4\u0dbb\u0db4\u0daf\u0dba \u0dba\u0dc5\u0dd2 \u0dc3\u0dd0\u0d9a\u0dc3\u0dd3\u0db8\u0dda \u0d9a\u0dda\u0dad\u0dba: " + code
                    + "\n\u0db8\u0dd9\u0db8 \u0d9a\u0dda\u0dad\u0dba \u0db8\u0dd2\u0db1\u0dd2\u0dad\u0dca\u0dad\u0dd4 15\u0d9a\u0dd2\u0db1\u0dca \u0d9a\u0dbd\u0dca \u0d89\u0d9a\u0dd4\u0dad\u0dca \u0dc0\u0dda.");
        } else {
            message.setSubject("FINORA - Password reset");
            message.setText("Password reset code: " + code + "\nExpires 15 minutes after the request.");
        }
        return message;
    }

    @PreDestroy
    void close() { executor.shutdownNow(); }
}
