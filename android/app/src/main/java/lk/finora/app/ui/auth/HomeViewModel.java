package lk.finora.app.ui.auth;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.AuthApi.RefreshRequest;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import okhttp3.ResponseBody;

public final class HomeViewModel extends ViewModel {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> signedOut = new MutableLiveData<>();
    private boolean signingOut;

    LiveData<Boolean> signedOut() {
        return signedOut;
    }

    void logout(Session expected) {
        if (signingOut) {
            return;
        }
        signingOut = true;
        worker.execute(() -> {
            TokenStore store = TokenStore.current();
            boolean failed = false;
            try {
                if (store != null) {
                    store.clear(expected);
                }
            } catch (IOException | RuntimeException exception) {
                failed = true;
            }
            signedOut.postValue(failed);
            revoke(expected);
        });
    }

    private static void revoke(Session session) {
        try {
            ApiClient client = new ApiClient(BuildConfig.API_BASE_URL, () -> null);
            try (ResponseBody unused = client.auth().logout(
                    new RefreshRequest(session.refresh)).execute().errorBody()) {
                // Local logout completes independently of remote revocation.
            }
        } catch (IOException | RuntimeException ignored) {
            // A network error cannot restore an invalidated local session.
        }
    }

    @Override
    protected void onCleared() {
        worker.shutdown();
    }
}
