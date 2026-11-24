package lk.finora.api.transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Validator;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.transaction.dto.SmsBatchRequest.Item;
import lk.finora.api.transaction.dto.SmsBatchResponse;
import lk.finora.api.transaction.dto.SmsBatchResponse.Status;

@Service
class SmsBatchService {
    private final TransactionService transactions;
    private final TransactionRepository repository;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    SmsBatchService(TransactionService transactions, TransactionRepository repository,
                    JdbcTemplate jdbc, Validator validator) {
        this.transactions = transactions;
        this.repository = repository;
        this.jdbc = jdbc;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SmsBatchResponse save(Long userId, List<Item> items) {
        // Serialize SMS batches for this user. The database unique key is the final invariant.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
        for (int i = 0; i < items.size(); i++) validate(userId, items.get(i), i + 1);

        var seen = new HashSet<>(repository.findExistingDedupHashes(userId,
                items.stream().map(Item::dedupHash).toList()));
        var results = new ArrayList<SmsBatchResponse.Item>(items.size());
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var txn = item.transaction();
            if (!seen.add(item.dedupHash())) {
                results.add(new SmsBatchResponse.Item(i + 1, Status.DUPLICATE, List.of()));
                continue;
            }
            var matches = repository.findManualMatches(userId, txn.accountId(), txn.txnDate(),
                    TransactionType.valueOf(txn.type()), txn.amount(), TransactionSource.MANUAL);
            transactions.createSmsImported(userId, txn, item.dedupHash());
            results.add(new SmsBatchResponse.Item(i + 1, Status.SAVED, matches));
        }
        return new SmsBatchResponse(results);
    }

    private void validate(Long userId, Item item, int index) {
        if (item == null || !validator.validate(item).isEmpty()) throw badItem(index, "Invalid item or hash");
        var txn = item.transaction();
        if (!validator.validate(txn).isEmpty() || txn.note() != null
                || !("SMS".equals(txn.source()) || "PASTE".equals(txn.source()))
                || txn.txnDate().getYear() < 1000 || txn.txnDate().getYear() > 9999) {
            throw badItem(index, "Invalid transaction fields");
        }
        try {
            transactions.checkNew(userId, txn);
        } catch (ApiException invalid) {
            throw badItem(index, invalid.getMessage());
        }
    }

    private static ApiException badItem(int index, String message) {
        return ApiException.badRequest("Item " + index + ": " + message);
    }
}
