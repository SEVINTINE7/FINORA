package lk.finora.app.ui.sms;

import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class SmsLocalTask {
    interface Operation<T> {
        T run(CancellationSignal signal, BooleanSupplier cancelled);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private CancellationSignal current;
    private Future<?> active;

    <T> void start(Operation<T> operation, Consumer<T> deliver, Runnable timedOut) {
        cancel();
        CancellationSignal signal = new CancellationSignal();
        current = signal;
        Runnable deadline = () -> {
            if (current != signal) return;
            cancel();
            timedOut.run();
        };
        main.postDelayed(deadline, TimeUnit.SECONDS.toMillis(3));
        active = executor.submit(() -> {
            long started = System.nanoTime();
            T result = operation.run(signal, () -> signal.isCanceled()
                    || Thread.currentThread().isInterrupted()
                    || System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(3));
            if (!signal.isCanceled()) main.post(() -> {
                if (current != signal || signal.isCanceled()) return;
                main.removeCallbacks(deadline);
                current = null;
                active = null;
                deliver.accept(result);
            });
        });
    }

    void cancel() {
        if (current != null) current.cancel();
        if (active != null) active.cancel(true);
        current = null;
        active = null;
        main.removeCallbacksAndMessages(null);
    }

    void close() {
        cancel();
        executor.shutdownNow();
    }
}
