package lk.finora.app.ui.sms;

import androidx.lifecycle.ViewModel;

import java.util.List;

import lk.finora.app.data.repo.TransactionRepository.SmsBatchItem;

/** Structured retry state for one live screen. No SavedStateHandle or disk storage. */
public final class SmsPendingBatch extends ViewModel {
    private Object login;
    private List<SmsBatchItem> items = List.of();

    void bind(Object identity) {
        if (login != identity) {
            clear();
            login = identity;
        }
    }

    List<SmsBatchItem> items() { return items; }

    void retain(List<SmsBatchItem> batch) { items = List.copyOf(batch); }

    void clear() { items = List.of(); }

    @Override protected void onCleared() {
        clear();
        login = null;
    }
}
