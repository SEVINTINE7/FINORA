package lk.finora.api.support;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import lk.finora.api.auth.AuthService;

/** Manual T55 test tooling. Never registered with the application. */
final class PersistentEvaluationDatasetLoader {
    static final String SCHEMA = "finora_t55_perf";
    static final String DIGEST = "14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944";

    record Target(int port, UUID serverId, String dataDirectory, String account) {
        Target {
            if (port < 1024 || port > 65535 || port == 3306 || port == 3307
                    || serverId == null || dataDirectory == null || dataDirectory.isBlank()
                    || !"finora_t55_loader@127.0.0.1".equals(account)) {
                throw new IllegalArgumentException("Dedicated evaluation target required");
            }
        }

        String url() {
            return "jdbc:mysql://127.0.0.1:" + port + "/" + SCHEMA;
        }
    }

    record Owner(long id, String email) {
        Owner {
            if (id <= 0 || email == null || !email.matches("finora-t55-[a-z0-9-]+@example\\.invalid")) {
                throw new IllegalArgumentException("Explicit disposable evaluation owner required");
            }
        }
    }

    record Totals(int transactions, BigDecimal income, BigDecimal expense) {
        BigDecimal net() {
            return income.subtract(expense);
        }
    }

    record Result(String digest, Map<String, Totals> totals, Map<String, Long> goals) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    PersistentEvaluationDatasetLoader(DataSource source) {
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    Result load(Target target, Owner worker, Owner irregular) {
        return load(target, worker, irregular, () -> {});
    }

    // Allows an integration test to verify rollback after the INSERTs.
    Result load(Target target, Owner worker, Owner irregular, Runnable beforeValidation) {
        if (worker.id() == irregular.id() || worker.email().equals(irregular.email())) {
            throw new IllegalArgumentException("Two distinct evaluation owners required");
        }
        return transaction.execute(status -> {
            if (!status.isNewTransaction()) {
                throw new IllegalStateException("The loader requires its own JDBC transaction");
            }
            verifyTarget(target);
            lockUntilTransactionCompletes();
            var registered = jdbc.query("SELECT id FROM users ORDER BY id",
                    (rs, row) -> rs.getLong(1));
            if (registered.size() != 2 || !registered.containsAll(List.of(worker.id(), irregular.id()))) {
                throw new IllegalStateException("Unexpected identities in evaluation schema");
            }
            verifyOwner(worker);
            verifyOwner(irregular);
            verifyEmpty();
            var seeder = new EvaluationDatasetSeeder(jdbc);
            var categories = seeder.resolveCategories();
            var loaded = seeder.insertFinancialRows(Map.of("worker", worker.id(), "irregular", irregular.id()), categories);
            beforeValidation.run();
            return verifyDataset(loaded);
        });
    }

    private void lockUntilTransactionCompletes() {
        // MySQL session locks survive COMMIT. Release only once the transaction ends.
        Integer acquired = jdbc.queryForObject("SELECT GET_LOCK('finora_t55_perf:load', 10)", Integer.class);
        require(Integer.valueOf(1).equals(acquired), "Another evaluation dataset load holds the lock");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                jdbc.queryForObject("SELECT RELEASE_LOCK('finora_t55_perf:load')", Integer.class);
            }
        });
    }

    void preflight(Target target) {
        transaction.executeWithoutResult(status -> verifyTarget(target));
    }

    private void verifyTarget(Target target) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            verifyConnection(connection, target);
            return null;
        });
        verifyServer(target);
    }

    static void verifyConnection(Connection connection, Target target) throws SQLException {
        if (!target.url().equals(connection.getMetaData().getURL())
                || !SCHEMA.equals(connection.getCatalog()) || connection.getAutoCommit()) {
            throw new IllegalStateException("Wrong evaluation JDBC endpoint, catalog or transaction state");
        }
    }

    private void verifyServer(Target target) {
        var matches = jdbc.queryForObject("""
                SELECT @@global.server_uuid = ? AND @@global.port = ?
                    AND @@global.bind_address = '127.0.0.1' AND @@global.datadir = ?
                    AND CURRENT_USER() = ?
                """, Boolean.class, target.serverId().toString(), target.port(), target.dataDirectory(), target.account());
        require(Boolean.TRUE.equals(matches), "Wrong evaluation MySQL instance, bind, data directory or account");
    }

    private void verifyOwner(Owner owner) {
        var eligible = jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u JOIN consents c ON c.user_id = u.id
                WHERE u.id = ? AND BINARY u.email = ? AND u.role = 'USER' AND u.status = 'ACTIVE'
                    AND u.password_hash LIKE '$2%' AND CHAR_LENGTH(u.password_hash) = 60
                    AND c.version = ? AND c.over18 = TRUE AND c.withdrawn_at IS NULL
                    AND c.id = (SELECT MAX(last.id) FROM consents last WHERE last.user_id = u.id)
                """, Integer.class, owner.id(), owner.email(), AuthService.CONSENT_VERSION);
        require(eligible == 1, "Unexpected or unconsented evaluation owner");
    }

    private void verifyEmpty() {
        for (String table : List.of("accounts", "transactions", "budgets", "bills", "bill_payments", "goals",
                "goal_contributions", "score_inputs", "health_scores", "insights", "monthly_history", "advice",
                "advice_ratings")) {
            require(count("SELECT COUNT(*) FROM " + table) == 0, "Evaluation financial state is not empty: " + table);
        }
        require(count("SELECT COUNT(*) FROM categories WHERE user_id IS NOT NULL") == 0,
                "Unexpected private categories in evaluation schema");
    }

    private Result verifyDataset(EvaluationDatasetSeeder.Loaded loaded) {
        var rows = new ArrayList<EvaluationDataset.Entry>();
        Map<String, Totals> totals = new LinkedHashMap<>();
        for (String profile : EvaluationDataset.PROFILES) {
            long owner = loaded.users().get(profile);
            long account = loaded.accounts().get(profile);
            long goal = loaded.goals().get(profile);
            boolean worker = profile.equals("worker");
            require(count("SELECT COUNT(*) FROM accounts WHERE user_id=? AND id=? AND type='CASH' AND opening_balance=0",
                    owner, account) == 1, "Unexpected evaluation account");
            require(count("""
                    SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id=t.account_id
                    LEFT JOIN categories c ON c.id=t.category_id
                    WHERE t.user_id=? AND (t.account_id<>? OR a.user_id<>t.user_id OR t.source<>'MANUAL'
                        OR t.note IS NOT NULL OR t.merchant IS NOT NULL OR (t.category_id IS NOT NULL
                            AND (c.id IS NULL OR c.user_id IS NOT NULL OR c.active=FALSE OR c.kind<>t.type)))
                    """, owner, account) == 0, "Unexpected evaluation transaction ownership or fields");
            var entries = jdbc.query("""
                    SELECT t.txn_date,t.type,c.name,t.amount FROM transactions t
                    LEFT JOIN categories c ON c.id=t.category_id WHERE t.user_id=? ORDER BY t.id
                    """, (rs, row) -> new EvaluationDataset.Entry(profile, rs.getDate(1).toLocalDate(),
                    rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), owner);
            rows.addAll(entries);
            var total = new Totals(entries.size(), sum(entries, "INCOME"), sum(entries, "EXPENSE"));
            require(total.equals(worker
                    ? new Totals(424, EvaluationDataset.money("720000"), EvaluationDataset.money("506099.99"))
                    : new Totals(402, EvaluationDataset.money("235250"), EvaluationDataset.money("117099.99"))),
                    "Unexpected evaluation totals");
            totals.put(profile, total);
            require(count("SELECT COUNT(*) FROM budgets WHERE user_id=?", owner) == 12, "Unexpected budget count");
            require(count("SELECT COUNT(*) FROM bills WHERE user_id=?", owner) == 1, "Unexpected bill count");
            require(count("SELECT COUNT(*) FROM goals WHERE id=? AND user_id=?", goal, owner) == 1,
                    "Unexpected goal ownership");
            require(count("SELECT COUNT(*) FROM score_inputs WHERE user_id=?", owner) == (worker ? 12 : 9),
                    "Unexpected score declaration count");
            require(count("""
                    SELECT COUNT(*) FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.user_id=?
                    """, owner) == (worker ? 12 : 6), "Unexpected paid marker count");
            require(count("SELECT COUNT(*) FROM goal_contributions WHERE goal_id=?", goal) == (worker ? 12 : 6),
                    "Unexpected contribution count");
        }
        String digest = EvaluationDataset.transactionDigest(rows);
        require(rows.equals(EvaluationDataset.entries()) && DIGEST.equals(digest), "Unexpected evaluation transaction content");
        return new Result(digest, Map.copyOf(totals), loaded.goals());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static BigDecimal sum(List<EvaluationDataset.Entry> rows, String type) {
        return rows.stream().filter(row -> row.type().equals(type)).map(EvaluationDataset.Entry::amount)
                .reduce(EvaluationDataset.money("0"), BigDecimal::add);
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
