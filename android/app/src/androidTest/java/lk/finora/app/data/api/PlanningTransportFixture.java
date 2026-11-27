package lk.finora.app.data.api;

import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.Field;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.local.TokenStore;
import okhttp3.Interceptor;

/** Scoped test-only transport fixture. The normal session and authentication path stays active. */
public final class PlanningTransportFixture implements AutoCloseable {
    private final Field slot;
    private final ApiClient previous;

    public PlanningTransportFixture(TokenStore store, Interceptor interceptor) throws ReflectiveOperationException {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        if (!BuildConfig.DEBUG || !"http://127.0.0.1:8080/".equals(BuildConfig.API_BASE_URL)
                || !"lk.finora.app.acceptance".equals(instrumentation.getTargetContext().getPackageName())
                || !"true".equals(InstrumentationRegistry.getArguments().getString("finoraIsolatedEmulator"))) {
            throw new IllegalStateException("The planning transport fixture requires the isolated acceptance emulator");
        }
        slot = SessionClient.class.getDeclaredField("client");
        slot.setAccessible(true);
        synchronized (SessionClient.class) {
            previous = (ApiClient) slot.get(null);
            slot.set(null, new ApiClient(BuildConfig.API_BASE_URL, store, interceptor));
        }
    }

    @Override public void close() throws IllegalAccessException {
        synchronized (SessionClient.class) {
            slot.set(null, previous);
        }
    }
}
