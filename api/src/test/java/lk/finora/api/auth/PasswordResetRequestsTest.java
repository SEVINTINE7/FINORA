package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PasswordResetRequestsTest {
    @Test void saturatedQueueRejectsWithoutRunningExtraWorkOnTheCaller(CapturedOutput output) throws Exception {
        var requests = new PasswordResetRequests();
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(64);
        var unexpected = new AtomicInteger();
        try {
            for (int i = 0; i < 2; i++) {
                requests.submit(() -> {
                    started.countDown();
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 64; i++) requests.submit(completed::countDown);
            requests.submit(unexpected::incrementAndGet);
            assertThat(completed.getCount()).isEqualTo(64L);
            assertThat(unexpected.get()).isZero();
            assertThat(output.getAll()).contains("Password reset request capacity reached");
        } finally {
            release.countDown();
            requests.close();
        }
        assertThat(completed.await(20, TimeUnit.SECONDS)).isTrue();
        assertThat(unexpected.get()).isZero();
    }

}
