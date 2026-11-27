package lk.finora.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** D64: only FINORA_MAIL_* configures this SMTP transport; TLS is mandatory. */
@Configuration(proxyBeanMethods = false)
class PasswordResetMailConfig {
    @Bean
    JavaMailSender passwordResetMailSender(@Value("${FINORA_MAIL_HOST:}") String host,
            @Value("${FINORA_MAIL_PORT:587}") int port,
            @Value("${FINORA_MAIL_USERNAME:}") String username,
            @Value("${FINORA_MAIL_PASSWORD:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setUsername(username);
        sender.setPassword(password);
        Properties props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        if (port == 465) {
            props.setProperty("mail.smtp.ssl.enable", "true");
        } else {
            props.setProperty("mail.smtp.starttls.enable", "true");
            props.setProperty("mail.smtp.starttls.required", "true");
        }
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "5000");
        props.setProperty("mail.smtp.writetimeout", "5000");
        props.setProperty("mail.debug", "false");
        return sender;
    }
}
