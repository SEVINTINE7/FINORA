package lk.finora.api.auth;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Queue the same work for every address. HTTP completion never waits for lookup, BCrypt or SMTP. */
@Component
class PasswordResetRequests {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequests.class);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread worker = new Thread(task, "finora-reset-request");
                worker.setDaemon(true);
                return worker;
            });

    void submit(Runnable request) {
        try {
            workers.execute(() -> {
                try {
                    request.run();
                } catch (RuntimeException unavailable) {
                    log.warn("Password reset request processing unavailable");
                }
            });
        } catch (RejectedExecutionException busy) {
            log.warn("Password reset request capacity reached");
        }
    }

    @PreDestroy
    void close() {
        workers.shutdown();
    }
}
