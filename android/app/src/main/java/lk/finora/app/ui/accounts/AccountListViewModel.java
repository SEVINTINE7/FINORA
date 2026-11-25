package lk.finora.app.ui.accounts;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.HttpFailure;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class AccountListViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final AccountRepository repository = work.requests == null ? null : new AccountRepository(work.requests);
    List<AccountDto> rows = java.util.Collections.emptyList();

    void load() {
        if (repository != null) work.run(repository::list, AccountRepository::rows, value -> rows = value, false);
    }

    void delete(long id) {
        work.run(() -> repository.delete(id), Work::empty, ignored -> load(), false);
    }

    private void clear() { rows = java.util.Collections.emptyList(); }

    @Override protected void onCleared() { work.close(); }

    public static final class Work {
        public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
        public final ProtectedRequests requests;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Runnable clear;
        private final Runnable sessionListener = () -> main.post(this::checkSession);
        private Call active;
        private boolean closed;
        public int error;
        public boolean ambiguousCreate;

        public Work(Runnable clear) {
            this(ProtectedRequests.currentUser(), clear);
        }

        public Work(ProtectedRequests requests, Runnable clear) {
            this.requests = requests;
            this.clear = clear;
            if (requests != null) requests.addListener(sessionListener);
        }

        public boolean isCurrent() {
            return !closed && requests != null && requests.isCurrent();
        }

        public boolean busy() { return active != null; }

        public void changed() {
            changes.setValue(changes.getValue() + 1);
        }

        private void checkSession() {
            if (!isCurrent()) {
                close();
                changed();
            }
        }

        public <T> void run(Supplier<Call> factory, Decoder<T> decoder,
                Consumer<T> success, boolean creation) {
            run(factory, decoder, success, creation, ignored -> {});
        }

        public <T> void run(Supplier<Call> factory, Decoder<T> decoder,
                Consumer<T> success, boolean creation, Consumer<Exception> onFailure) {
            if (!isCurrent() || busy()) return;
            error = 0;
            Call call;
            try {
                call = factory.get();
            } catch (IllegalStateException changedLogin) {
                checkSession();
                return;
            }
            active = call;
            changed();
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException failure) {
                    finish(call, null, failure, success, creation, onFailure);
                }

                @Override public void onResponse(Call call, Response response) {
                    try (response) {
                        finish(call, decoder.decode(response), null, success, creation, onFailure);
                    } catch (IOException | RuntimeException failure) {
                        finish(call, null, failure, success, creation, onFailure);
                    }
                }
            });
        }

        private <T> void finish(Call call, T result, Exception failure,
                Consumer<T> success, boolean creation, Consumer<Exception> onFailure) {
            main.post(() -> {
                boolean currentLogin = isCurrent();
                if (!acceptsResult(currentLogin, active, call)) {
                    if (!currentLogin) checkSession();
                    return;
                }
                active = null;
                if (failure == null) {
                    success.accept(result);
                } else {
                    error = message(failure);
                    ambiguousCreate = isAmbiguousCreate(creation, failure);
                    if (ambiguousCreate) error = R.string.records_uncertain;
                    onFailure.accept(failure);
                }
                changed();
            });
        }

        static boolean acceptsResult(boolean currentLogin, Call active, Call completed) {
            return currentLogin && active != null && active == completed;
        }

        static boolean isAmbiguousCreate(boolean creation, Exception failure) {
            return creation && (!(failure instanceof HttpFailure http) || http.status >= 500);
        }

        public static Void empty(Response response) throws IOException {
            AccountRepository.requireSuccess(response);
            return null;
        }

        public static int message(Exception failure) {
            if (failure instanceof HttpFailure http) {
                if ("IN_USE".equals(http.code)) return R.string.records_in_use;
                if ("CONFLICT".equals(http.code)) return R.string.records_duplicate;
                if (http.status == 404) return R.string.records_missing;
                if (http.status == 401 || http.status == 403) return R.string.records_forbidden;
                if (http.status == 400) return R.string.records_invalid;
            }
            return R.string.records_failed;
        }

        public void close() {
            if (closed) return;
            closed = true;
            if (requests != null) requests.removeListener(sessionListener);
            if (active != null) active.cancel();
            active = null;
            clear.run();
        }

        public interface Decoder<T> {
            T decode(Response response) throws IOException;
        }
    }
}
