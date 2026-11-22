package lk.finora.app.ui.accounts;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;

import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class AccountFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final AccountRepository repository = work.requests == null ? null : new AccountRepository(work.requests);
    long id;
    boolean ready;
    boolean saved;
    String name = "";
    String amount = "";
    int type;
    static final String[] TYPES = {"BANK", "CASH", "CARD", "WALLET"};

    void load(long recordId) {
        id = recordId;
        if (id == 0) {
            ready = true;
            work.changed();
        } else if (repository != null) {
            work.run(() -> repository.get(id), response -> AccountRepository.read(response, AccountDto.class),
                    item -> {
                        name = item.name();
                        amount = item.openingBalance().toPlainString();
                        type = java.util.Arrays.asList(TYPES).indexOf(item.type());
                        ready = true;
                    }, false);
        }
    }

    void save(BigDecimal balance) {
        if (!ready || work.ambiguousCreate || !work.isCurrent() || work.busy()) return;
        AccountDto.Write body = new AccountDto.Write(name.trim(), TYPES[type], balance);
        work.run(() -> id == 0 ? repository.create(body) : repository.update(id, body),
                response -> AccountRepository.read(response, AccountDto.class), value -> saved = true, id == 0);
    }

    static BigDecimal parseBalance(String value) {
        String text = value.trim();
        if (!text.matches("-?[0-9]{1,13}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Invalid amount");
        }
        return new BigDecimal(text).setScale(2);
    }

    private void clear() {
        name = "";
        amount = "";
        ready = false;
    }

    @Override protected void onCleared() { work.close(); }
}
