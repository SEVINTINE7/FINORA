package lk.finora.app.ui.login;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.LoginRepository;
import lk.finora.app.data.repo.LoginRepository.Attempt;
import lk.finora.app.data.repo.LoginRepository.Result;

public final class LoginViewModel extends AndroidViewModel {
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Attempt activeAttempt;
    private LoginRepository repository;
    private boolean restorationStarted;
    private volatile boolean cleared;
    private String consentGrant;
    private retrofit2.Call<?> consentCall;

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<State> state() {
        return state;
    }

    void restore(boolean storageError) {
        if (restorationStarted) {
            return;
        }
        restorationStarted = true;
        if (storageError) {
            state.setValue(new State(false, Result.STORAGE_ERROR));
            return;
        }
        retryRestore();
    }

    void retryRestore() {
        if (state.getValue().loading) {
            return;
        }
        state.setValue(new State(true, null));
        worker.execute(() -> {
            Result result = restoreSession();
            main.post(() -> {
                if (!cleared) {
                    state.setValue(new State(false, result));
                }
            });
        });
    }

    private Result restoreSession() {
        if (BuildConfig.API_BASE_URL.isEmpty()) {
            return Result.CONFIGURATION_ERROR;
        }
        try {
            TokenStore store = TokenStore.open(getApplication());
            if (store.verifiedSession() != null) {
                return Result.SUCCESS;
            }
            Session stored = store.session();
            if (stored == null) {
                return null;
            }
            switch (SessionClient.get(store).sessions().refresh(stored)) {
                case RESTORED: return Result.SUCCESS;
                case DISABLED: return Result.ACCOUNT_DISABLED;
                case EXPIRED: return Result.SESSION_EXPIRED;
                case STORAGE_ERROR: return Result.STORAGE_ERROR;
                case SUPERSEDED: return null;
                default: return Result.RESTORE_UNAVAILABLE;
            }
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            return Result.STORAGE_ERROR;
        }
    }

    void submit(String email, String password) {
        if (activeAttempt != null || state.getValue().loading) {
            return;
        }
        Attempt attempt = new Attempt();
        activeAttempt = attempt;
        state.setValue(new State(true, null));
        worker.execute(() -> {
            Result result = login(email, password, attempt);
            main.post(() -> {
                if (activeAttempt == attempt) {
                    activeAttempt = null;
                    consentGrant = attempt.takeConsentGrant();
                    state.setValue(new State(false, result));
                }
            });
        });
    }

    private Result login(String email, String password, Attempt attempt) {
        if (BuildConfig.API_BASE_URL.isEmpty()) {
            return Result.CONFIGURATION_ERROR;
        }
        try {
            if (repository == null) {
                TokenStore tokens = TokenStore.open(getApplication());
                ApiClient client = SessionClient.get(tokens);
                repository = new LoginRepository(client, (LoginRepository.SessionWriter) tokens::save);
            }
            return repository.login(email, password, attempt);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            return Result.STORAGE_ERROR;
        }
    }

    void cancelConsent() {
        consentGrant = null;
        if (state.getValue().result == Result.CONSENT_REQUIRED) state.setValue(new State(false, null));
    }

    void acceptConsent() {
        consentAction(api -> api.acceptConsent(apiHeader(), new lk.finora.app.data.api.AuthApi.ConsentAcceptance(
                lk.finora.app.data.repo.RegisterRepository.CONSENT_VERSION, true)),
                ignored -> {}, Result.CONSENT_RESTORED);
    }

    void deleteConsentData(String password) {
        consentAction(api -> api.deleteConsentData(apiHeader(), new lk.finora.app.data.api.AuthApi.Deletion(password)),
                ignored -> {}, Result.CONSENT_ACCOUNT_DELETED);
    }

    void exportConsentData(android.net.Uri destination) {
        consentAction(api -> api.exportConsentData(apiHeader()), body -> {
            if (body == null) throw new IOException("Export unavailable");
            try (body; var output = getApplication().getContentResolver().openOutputStream(destination, "wt");
                    var input = body.byteStream()) {
                if (output == null) throw new IOException("Export destination unavailable");
                byte[] buffer = new byte[8192];
                for (int count; !cleared && (count = input.read(buffer)) != -1;) {
                    output.write(buffer, 0, count);
                }
            }
        }, Result.CONSENT_EXPORT_SAVED);
    }

    private String apiHeader() { return "Consent " + consentGrant; }

    private <T> void consentAction(java.util.function.Function<lk.finora.app.data.api.AuthApi, retrofit2.Call<T>> request,
            ConsentResponse<T> onSuccess, Result success) {
        if (consentGrant == null || consentCall != null) return;
        // A grant is in memory only, and each operation is explicitly requested once.
        retrofit2.Call<T> call = request.apply(ApiClient.consentRecovery(BuildConfig.API_BASE_URL));
        consentGrant = null;
        consentCall = call;
        state.setValue(new State(true, null));
        worker.execute(() -> {
            Result outcome = Result.CONSENT_RETRY;
            try {
                var response = call.execute();
                if (response.isSuccessful()) {
                    onSuccess.read(response.body());
                    outcome = success;
                } else if (response.errorBody() != null) {
                    response.errorBody().close();
                }
            } catch (IOException | RuntimeException failure) {
                // The outcome can be uncertain. Require fresh password authentication before any retry.
            }
            Result result = outcome;
            main.post(() -> {
                if (!cleared && consentCall == call) {
                    consentCall = null;
                    state.setValue(new State(false, result));
                }
            });
        });
    }

    private interface ConsentResponse<T> { void read(T response) throws IOException; }

    @Override
    protected void onCleared() {
        cleared = true;
        consentGrant = null;
        if (consentCall != null) consentCall.cancel();
        consentCall = null;
        Attempt attempt = activeAttempt;
        activeAttempt = null;
        if (attempt != null) {
            attempt.cancel();
        }
        // Retire the worker without interrupting a preference commit already underway.
        worker.shutdown();
    }

    static final class State {
        final boolean loading;
        final Result result;

        State(boolean loading, Result result) {
            this.loading = loading;
            this.result = result;
        }
    }
}
