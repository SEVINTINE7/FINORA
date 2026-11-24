package lk.finora.api.history;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@EnableScheduling
class HistoryScheduler {
    private final HistoryService history;
    private final HistoryRecovery recovery;
    HistoryScheduler(HistoryService history, HistoryRecovery recovery) {
        this.history = history;
        this.recovery = recovery;
    }

    // Always targets the previous Colombo month. Repeating the scan resumes interrupted runs.
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Colombo")
    @Scheduled(fixedDelayString = "${finora.history.backfill-delay-ms:300000}")
    void makePreviousMonth() {
        String month = previousMonth(Instant.now()).toString();
        for (var key : history.missing(month)) process(key, false);
    }

    static YearMonth previousMonth(Instant now) {
        return YearMonth.from(now.atZone(ZoneId.of("Asia/Colombo"))).minusMonths(1);
    }

    @Scheduled(fixedDelayString = "${finora.history.recovery-delay-ms:300000}")
    void recoverMissedMonths() {
        recovery.run(Instant.now());
    }

    @Scheduled(fixedDelayString = "${finora.history.refresh-delay-ms:5000}")
    void refreshExisting() {
        for (var key : history.dirty()) process(key, true);
    }

    private void process(HistoryService.Key key, boolean existingOnly) {
        try {
            history.materialize(key, existingOnly);
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(HistoryScheduler.class)
                    .warn("Monthly history work failed and remains pending: {}", failure.getClass().getSimpleName());
        }
    }
}
