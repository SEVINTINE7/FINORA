package lk.finora.app.acceptance;

import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import androidx.test.platform.app.InstrumentationRegistry;

/** Measures a hardware frame committed after a screen-specific content oracle succeeds. */
final class RenderedContentProbe {
    enum Outcome { COMMITTED, FAILED, TIMED_OUT }

    record Sample(long actionNs, long contentReadyNs, long frameCommittedNs, Outcome outcome) {}

    private final Predicate<View> usableContent;
    private final String traceKey;
    private final boolean recordVsync;
    private final CompletableFuture<Sample> finished = new CompletableFuture<>();
    private volatile long actionNs;
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener preDraw;
    private Runnable frameCallback;
    private long vsyncId;
    private android.view.Choreographer.VsyncCallback vsync;

    RenderedContentProbe(Predicate<View> usableContent, String traceKey, boolean recordVsync) {
        this.usableContent = usableContent;
        this.traceKey = traceKey;
        this.recordVsync = recordVsync;
    }

    /** Invoke on the UI thread at the action boundary, before dispatching the action. */
    void markAction() {
        requireMainThread();
        if (actionNs != 0) throw new IllegalStateException("Attempt already started");
        actionNs = SystemClock.elapsedRealtimeNanos();
        trace("action");
    }

    /** Attach to the actual destination window before its first eligible draw. */
    void watch(Activity destination) {
        requireMainThread();
        if (actionNs == 0 || observer != null) throw new IllegalStateException("Expected one active attempt");
        var window = destination.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT < 29) {
            finished.complete(new Sample(actionNs, 0, 0, Outcome.FAILED));
            return;
        }
        observer = window.getViewTreeObserver();
        if (recordVsync && Build.VERSION.SDK_INT >= 33) {
            vsync = data -> {
                vsyncId = data.getPreferredFrameTimeline().getVsyncId();
                if (!finished.isDone() && frameCallback == null) {
                    android.view.Choreographer.getInstance().postVsyncCallback(vsync);
                }
            };
            android.view.Choreographer.getInstance().postVsyncCallback(vsync);
        }
        preDraw = () -> {
            if (!finished.isDone() && frameCallback == null && window.isHardwareAccelerated() && usableContent.test(window)) {
                long ready = SystemClock.elapsedRealtimeNanos();
                trace("ready");
                if (vsyncId > 0) trace("vsync-" + vsyncId);
                frameCallback = () -> {
                    frameCallback = null;
                    trace("submitted");
                    finished.complete(new Sample(actionNs, ready, SystemClock.elapsedRealtimeNanos(),
                            Outcome.COMMITTED));
                };
                observer = window.getViewTreeObserver();
                observer.registerFrameCommitCallback(frameCallback);
            }
            return true;
        };
        observer.addOnPreDrawListener(preDraw);
        window.invalidate();
    }

    Sample await(Duration timeout) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper() || actionNs == 0) {
            throw new IllegalStateException("Await a started sample off the UI thread");
        }
        try {
            long remaining = timeout.toNanos() - (SystemClock.elapsedRealtimeNanos() - actionNs);
            return finished.get(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.TimeoutException elapsed) {
            var timedOut = new Sample(actionNs, 0, 0, Outcome.TIMED_OUT);
            finished.complete(timedOut);
            return finished.join();
        } catch (java.util.concurrent.ExecutionException unexpected) {
            throw new IllegalStateException("Frame collection failed", unexpected);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(this::detach);
        }
    }

    private void detach() {
        if (Build.VERSION.SDK_INT >= 33 && vsync != null) {
            android.view.Choreographer.getInstance().removeVsyncCallback(vsync);
        }
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(preDraw);
            if (Build.VERSION.SDK_INT >= 29 && frameCallback != null) {
                observer.unregisterFrameCommitCallback(frameCallback);
            }
        }
    }

    private void trace(String event) {
        android.os.Trace.beginSection("T55-" + traceKey + "-" + event);
        android.os.Trace.endSection();
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Frame observation requires the UI thread");
        }
    }
}
