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

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<State> state() {
        return state;
    }

    void submit(String email, String password) {
        if (activeAttempt != null) {
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
                repository = new LoginRepository(new ApiClient(BuildConfig.API_BASE_URL,
                        tokens::accessToken), (LoginRepository.SessionWriter) tokens::save);
            }
            return repository.login(email, password, attempt);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            return Result.STORAGE_ERROR;
        }
    }

    @Override
    protected void onCleared() {
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
