package lk.finora.api.account;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.account.dto.AccountRequest;
import lk.finora.api.account.dto.AccountResponse;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;

/** A user's own accounts (T09.1). Every method takes the caller's id, so users only reach their own rows. */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        List<AccountResponse> result = new ArrayList<>();
        for (Account account : accountRepository.findByUserIdOrderById(userId)) {
            result.add(toResponse(account));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long userId, Long id) {
        return toResponse(findOwn(userId, id));
    }

    @Transactional
    public AccountResponse create(Long userId, AccountRequest request) {
        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndName(userId, name)) {
            throw duplicateName();
        }

        Account account = new Account();
        account.setUserId(userId);
        account.setName(name);
        account.setType(AccountType.valueOf(request.type()));
        account.setOpeningBalance(request.openingBalance());
        accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, AccountRequest request) {
        Account account = findOwn(userId, id);
        String name = request.name().trim();
        if (accountRepository.existsByUserIdAndNameAndIdNot(userId, name, id)) {
            throw duplicateName();
        }

        account.setName(name);
        account.setType(AccountType.valueOf(request.type()));
        account.setOpeningBalance(request.openingBalance());
        accountRepository.save(account);
        return toResponse(account);
    }

    /** Deletes the account, or only archives it when transactions use it, so no money records are lost. */
    @Transactional
    public void delete(Long userId, Long id) {
        Account account = findOwn(userId, id);
        if (accountRepository.countTransactions(id) > 0) {
            account.setArchived(true);
            accountRepository.save(account);
        } else {
            accountRepository.delete(account);
        }
    }

    // Another user's account gives the same 404 as a missing one (never 403)
    private Account findOwn(Long userId, Long id) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    private ApiException duplicateName() {
        return new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT, "You already have an account with this name");
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getType().name(),
                account.getOpeningBalance(), account.isArchived());
    }
}
