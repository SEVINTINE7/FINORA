package lk.finora.api.notification;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** T34.2: daily alert check at 08:00 Sri Lanka time, plus a retry pass for unsent events. */
@Component
@Profile("!test")
@EnableScheduling
class AlertScheduler {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final AlertService alerts;

    AlertScheduler(AlertService alerts) {
        this.alerts = alerts;
    }

    @Scheduled(cron = "${finora.alerts.cron:0 0 8 * * *}", zone = "Asia/Colombo")
    void daily() {
        int created = alerts.runDaily(LocalDate.now(COLOMBO));
        LoggerFactory.getLogger(AlertScheduler.class).info("Daily alert check created {} events", created);
    }

    @Scheduled(initialDelayString = "${finora.alerts.retry-delay-ms:900000}",
            fixedDelayString = "${finora.alerts.retry-delay-ms:900000}")
    void retry() {
        alerts.sendPending();
    }
}
