package lk.finora.app.ui.login;

import android.app.Application;

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
import lk.finora.app.data.repo.LoginRepository.Result;

public final class LoginViewModel extends AndroidViewModel {
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LoginRepository repository;

    public LoginViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<State> state() {
        return state;
    }

    void submit(String email, String password) {
        State current = state.getValue();
        if (current != null && current.loading) {
            return;
        }
        state.setValue(new State(true, null));
        worker.execute(() -> state.postValue(new State(false, login(email, password))));
    }

    private Result login(String email, String password) {
        if (BuildConfig.API_BASE_URL.isEmpty()) {
            return Result.CONFIGURATION_ERROR;
        }
        try {
            if (repository == null) {
                TokenStore tokens = new TokenStore(getApplication());
                repository = new LoginRepository(new ApiClient(BuildConfig.API_BASE_URL,
                        tokens::accessToken), tokens);
            }
            return repository.login(email, password);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            return Result.STORAGE_ERROR;
        }
    }

    @Override
    protected void onCleared() {
        worker.shutdownNow();
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
