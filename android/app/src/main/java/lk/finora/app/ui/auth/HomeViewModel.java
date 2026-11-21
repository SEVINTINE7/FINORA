package lk.finora.app.ui.auth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

public final class HomeViewModel extends ViewModel {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> signedOut = new MutableLiveData<>();
    private boolean signingOut;

    LiveData<Boolean> signedOut() {
        return signedOut;
    }

    boolean signingOut() {
        return signingOut;
    }

    void logout(Session expected) {
        if (signingOut) {
            return;
        }
        signingOut = true;
        worker.execute(() -> {
            TokenStore store = TokenStore.current();
            if (store == null) {
                signedOut.postValue(false);
                return;
            }
            TokenStore.LogoutResult result = store.clearAccount(expected);
            signedOut.postValue(result.storageFailure != null);
            if (result.invalidated != null) {
                SessionClient.get(store).sessions().revoke(result.invalidated.refresh);
            }
        });
    }

    @Override
    protected void onCleared() {
        worker.shutdown();
    }
}
