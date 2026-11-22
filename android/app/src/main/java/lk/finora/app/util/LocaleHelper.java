package lk.finora.app.util;

import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class LocaleHelper {
    private LocaleHelper() {}

    public static Button selector(AppCompatActivity activity, Consumer<String> select) {
        Button button = new Button(activity);
        button.setText(R.string.language_selector);
        button.setMinHeight(Math.round(48 * activity.getResources().getDisplayMetrics().density));
        button.setAllCaps(false);
        button.setOnClickListener(view -> new AlertDialog.Builder(activity)
                .setTitle(R.string.language_selector)
                .setItems(new String[]{activity.getString(R.string.language_english),
                        activity.getString(R.string.language_sinhala)}, (dialog, position) ->
                        select.accept(position == 0 ? "EN" : "SI"))
                .show());
        return button;
    }

    public static void apply(String language) {
        String tag = "SI".equals(language) ? "si" : "en";
        LocaleListCompat selected = LocaleListCompat.forLanguageTags(tag);
        if (!selected.equals(AppCompatDelegate.getApplicationLocales())) {
            AppCompatDelegate.setApplicationLocales(selected);
        }
    }

    public static final class ProfileLanguage extends ViewModel {
        private static final ConfirmedLanguage confirmed = new ConfirmedLanguage();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private final MutableLiveData<State> state = new MutableLiveData<>();
        private Session entry;
        private volatile Call inFlight;
        private volatile boolean closed;

        public LiveData<State> state() {
            return state;
        }

        public void initialize(Session session) {
            if (entry == null && "USER".equals(session.role)) {
                entry = session;
                state.setValue(new State(confirmed.forLogin(session.loginIdentity(), session.language),
                        false, false));
                run(null);
            }
        }

        public void select(String language) {
            State current = state.getValue();
            if (entry != null && current != null && !current.busy) {
                run(language);
            }
        }

        public boolean isCurrent() {
            TokenStore store = TokenStore.current();
            return entry != null && store != null && entry.sameAccount(store.verifiedSession());
        }

        private void run(String requested) {
            if (!isCurrent()) {
                return;
            }
            State previous = state.getValue();
            state.setValue(new State(previous.language, true, false));
            worker.execute(() -> {
                String saved = previous.language;
                boolean failed = false;
                boolean synchronizedWithProfile = false;
                try {
                    saved = synchronize(requested, this::exchange);
                    synchronizedWithProfile = true;
                } catch (IOException | RuntimeException failure) {
                    failed = requested != null;
                }
                State next = new State(saved, false, failed);
                boolean remember = synchronizedWithProfile;
                main.post(() -> {
                    TokenStore store = TokenStore.current();
                    Session current = store == null ? null : store.verifiedSession();
                    if (!closed && entry.sameAccount(current)) {
                        if (remember) {
                            confirmed.record(current.loginIdentity(), next.language);
                        }
                        state.setValue(next);
                    }
                });
            });
        }

        private Profile exchange(LanguageUpdate update) throws IOException {
            TokenStore store = TokenStore.current();
            if (store == null) {
                throw new IOException("No active session");
            }
            Gson gson = new Gson();
            Request.Builder request = new Request.Builder().url(BuildConfig.API_BASE_URL + "api/v1/me");
            if (update != null) {
                request.put(RequestBody.create(gson.toJson(update), MediaType.get("application/json")));
            }
            Call call;
            synchronized (store) {
                if (closed || !isCurrent()) {
                    throw new IOException("Profile session changed");
                }
                call = SessionClient.get(store).newCall(request.build());
            }
            inFlight = call;
            try {
                if (closed) {
                    call.cancel();
                }
                try (Response response = call.execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("Profile request failed");
                    }
                    Profile profile = gson.fromJson(body.charStream(), Profile.class);
                    if (profile == null || profile.fullName == null || profile.fullName.trim().isEmpty()
                            || !("EN".equals(profile.language) || "SI".equals(profile.language))) {
                        throw new IOException("Invalid profile response");
                    }
                    return profile;
                }
            } finally {
                inFlight = null;
            }
        }

        @Override
        protected void onCleared() {
            closed = true;
            Call call = inFlight;
            if (call != null) {
                call.cancel();
            }
            worker.shutdownNow();
        }
    }

    // The persisted AppCompat locale covers a cold offline start. This cache also
    // covers a new home ViewModel for the same already-verified login.
    static final class ConfirmedLanguage {
        private Object identity;
        private String language;

        String forLogin(Object login, String sessionLanguage) {
            if (login == identity) {
                return language;
            }
            identity = null;
            language = null;
            return sessionLanguage;
        }

        void record(Object login, String confirmed) {
            identity = login;
            language = confirmed;
        }
    }

    public static final class State {
        public final String language;
        public final boolean busy;
        public final boolean failed;

        private State(String language, boolean busy, boolean failed) {
            this.language = language;
            this.busy = busy;
            this.failed = failed;
        }
    }

    static String synchronize(String requested, ProfileEndpoint endpoint) throws IOException {
        Profile profile = endpoint.exchange(null);
        if (requested != null && !requested.equals(profile.language)) {
            profile = endpoint.exchange(new LanguageUpdate(profile.fullName, requested));
            if (!requested.equals(profile.language)) {
                throw new IOException("Language update was not confirmed");
            }
        }
        return profile.language;
    }

    interface ProfileEndpoint {
        Profile exchange(LanguageUpdate update) throws IOException;
    }

    static final class Profile {
        String fullName;
        String language;
    }

    static final class LanguageUpdate {
        final String fullName;
        final String language;

        LanguageUpdate(String fullName, String language) {
            this.fullName = fullName;
            this.language = language;
        }
    }
}
