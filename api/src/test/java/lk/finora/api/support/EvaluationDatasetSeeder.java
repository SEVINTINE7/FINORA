package lk.finora.api.support;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit test helper only. No migration, application bean, startup hook, deletion or commit. */
public final class EvaluationDatasetSeeder {
    public record Loaded(Map<String, Long> users, Map<String, Long> accounts, Map<String, Long> goals) {}
    private final JdbcTemplate jdbc;
    public EvaluationDatasetSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static void validateTarget(String url, String catalog, boolean transactional, boolean autoCommit) {
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/finora_test(?:\\?.*)?")
                || !"finora_test".equals(catalog) || !transactional || autoCommit)
            throw new IllegalStateException("Evaluation seed requires a loopback finora_test transaction");
    }

    public Loaded seed() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            validateTarget(connection.getMetaData().getURL(), connection.getCatalog(),
                    TransactionSynchronizationManager.isActualTransactionActive(), connection.getAutoCommit());
            return null;
        });
        String workerEmail = "finora-t55-v1-worker@example.invalid";
        String irregularEmail = "finora-t55-v1-irregular@example.invalid";
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email IN (?,?)", Integer.class,
                workerEmail, irregularEmail) != 0) throw new IllegalStateException("Synthetic fixture identities already exist; seed refused");
        // Resolve all reference data before the first write; never create/replace shared categories.
        Map<String, Long> categories = new LinkedHashMap<>();
        for (var entry : EvaluationDataset.entries()) if (entry.category() != null) {
            String key = entry.type() + ":" + entry.category();
            if (!categories.containsKey(key)) categories.put(key, jdbc.queryForObject(
                    "SELECT id FROM categories WHERE user_id IS NULL AND active=TRUE AND kind=? AND name=?",
                    Long.class, entry.type(), entry.category()));
        }
        Map<String, Long> users = new LinkedHashMap<>(), accounts = new LinkedHashMap<>(), goals = new LinkedHashMap<>();
        for (String profile : EvaluationDataset.PROFILES) {
            boolean worker = profile.equals("worker");
            String email = worker ? workerEmail : irregularEmail;
            jdbc.update("INSERT INTO users(email,password_hash,full_name,role) VALUES (?,'test-only-not-a-login-hash',?,'USER')",
                    email, "SYNTHETIC T55 " + profile);
            long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
            users.put(profile, user);
            jdbc.update("INSERT INTO accounts(user_id,name,type,opening_balance) VALUES (?,'SYNTHETIC wallet','CASH',0)", user);
            long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user);
            accounts.put(profile, account);
            jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id,created_at) VALUES (?,?,?,31,?,'2025-12-01 00:00:00')",
                    user, "SYNTHETIC " + profile + " recurring bill", EvaluationDataset.money(worker ? "2000" : "1000"),
                    categories.get("EXPENSE:" + (worker ? "Utilities" : "Mobile/Internet")));
            long bill = jdbc.queryForObject("SELECT id FROM bills WHERE user_id=?", Long.class, user);
            jdbc.update("INSERT INTO goals(user_id,name,target_amount,status,created_at) VALUES (?,?,?,?, '2025-12-01 00:00:00')",
                    user, "SYNTHETIC " + profile + " goal", EvaluationDataset.money(worker ? "24000" : "10000"), worker ? "DONE" : "ACTIVE");
            long goal = jdbc.queryForObject("SELECT id FROM goals WHERE user_id=?", Long.class, user);
            goals.put(profile, goal);
            for (int i = 0; i < 12; i++) {
                var month = EvaluationDataset.START.plusMonths(i);
                jdbc.update("INSERT INTO budgets(user_id,category_id,month,limit_amount) VALUES (?,?,?,?)",
                        user, categories.get("EXPENSE:Shopping"), month.toString(), EvaluationDataset.money(worker ? "5000" : "1000"));
                if (worker || i % 2 == 0) {
                    jdbc.update("INSERT INTO bill_payments(bill_id,month,paid_at) VALUES (?,?,?)", bill, month.toString(), month.atEndOfMonth().atStartOfDay());
                    jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,?,?)",
                            goal, EvaluationDataset.money(worker ? "2000" : "500"), month.atDay(20));
                }
                if (worker || i % 4 != 0) {
                    boolean unknown = !worker && i == 5;
                    // Explicit invented declarations, never balances inferred from transactions/goals.
                    jdbc.update("INSERT INTO score_inputs(user_id,month,emergency_reserve,reserve_as_of,no_applicable_debt,no_applicable_goals,confirmed_at) "
                            + "VALUES (?,?,?,?,TRUE,FALSE,?)", user, month.toString(), unknown ? null : EvaluationDataset.money(Integer.toString(i * (worker ? 1000 : 100))),
                            unknown ? null : month.atDay(1), month.atDay(1).atStartOfDay());
                }
            }
        }
        for (var entry : EvaluationDataset.entries()) jdbc.update(
                "INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,source) VALUES (?,?,?,?,?,?,'MANUAL')",
                users.get(entry.profile()), accounts.get(entry.profile()), entry.category() == null ? null : categories.get(entry.type() + ":" + entry.category()),
                entry.type(), entry.amount(), entry.date());
        return new Loaded(Map.copyOf(users), Map.copyOf(accounts), Map.copyOf(goals));
    }
}
