package lk.finora.app.ui.register;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.RegisterRepository;
import lk.finora.app.data.repo.RegisterRepository.Result;

/** One register request at a time; survives rotation so a slow request is not sent twice. */
public final class RegisterViewModel extends AndroidViewModel {
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cleared;

    public RegisterViewModel(@NonNull Application application) {
        super(application);
    }

    LiveData<State> state() {
        return state;
    }

    boolean busy() {
        return state.getValue().loading;
    }

    void submit(String email, String password, String fullName, String language, boolean over18) {
        if (busy()) return;
        state.setValue(new State(true, null));
        worker.execute(() -> {
            Result result;
            if (BuildConfig.API_BASE_URL.isEmpty()) {
                result = Result.SERVER_ERROR;
            } else {
                try {
                    RegisterRepository repository =
                            new RegisterRepository(SessionClient.get(TokenStore.open(getApplication())));
                    result = repository.register(email, password, fullName, language, over18);
                } catch (Exception storageOrSetup) {
                    result = Result.SERVER_ERROR;
                }
            }
            Result done = result;
            main.post(() -> {
                if (!cleared) state.setValue(new State(false, done));
            });
        });
    }

    /** Clears a shown error once the user starts editing again. */
    void acknowledge() {
        State current = state.getValue();
        if (!current.loading && current.result != null && current.result != Result.SUCCESS) {
            state.setValue(new State(false, null));
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
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
