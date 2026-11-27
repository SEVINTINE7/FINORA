package lk.finora.api.auth;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PasswordResetMail {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMail.class);
    private final JavaMailSenderImpl sender;
    private final TaskExecutor executor;
    private final String from;

    @org.springframework.beans.factory.annotation.Autowired
    PasswordResetMail(@Value("${FINORA_MAIL_HOST:}") String host, @Value("${FINORA_MAIL_PORT:587}") int port,
            @Value("${FINORA_MAIL_USERNAME:}") String username, @Value("${FINORA_MAIL_PASSWORD:}") String password,
            @Value("${FINORA_MAIL_FROM:}") String from, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this(host, port, username, password, from, executor, new JavaMailSenderImpl());
    }

    PasswordResetMail(String host, int port, String username, String password, String from,
            TaskExecutor executor, JavaMailSenderImpl sender) {
        this.sender = sender;
        this.executor = executor;
        this.from = from;
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        var props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        if (port == 465) {
            props.setProperty("mail.smtp.ssl.enable", "true");
        } else {
            props.setProperty("mail.smtp.starttls.enable", "true");
            props.setProperty("mail.smtp.starttls.required", "true");
        }
        for (String key : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            props.setProperty("mail.smtp." + key, "5000");
        }
    }

    boolean configured() {
        return !sender.getHost().isBlank() && !from.isBlank() && !sender.getUsername().isBlank()
                && !sender.getPassword().isBlank();
    }

    static SimpleMailMessage message(String from, String to, String language, String code) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        boolean sinhala = "SI".equals(language);
        message.setSubject(sinhala ? "FINORA මුරපදය යළි සැකසීම" : "FINORA password reset");
        message.setText(sinhala ? "කේතය: " + code + "\nවිනාඩි 15 කින් කල් ඉකුත් වේ."
                : "Code: " + code + "\nExpires in 15 minutes.");
        return message;
    }

    void afterCommit(String to, String language, String code) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    executor.execute(() -> {
                        try {
                            sender.send(message(from, to, language, code));
                        } catch (RuntimeException failed) {
                            log.warn("Password reset mail delivery failed");
                        }
                    });
                } catch (RuntimeException unavailable) {
                    log.warn("Password reset mail dispatch unavailable");
                }
            }
        });
    }
}
