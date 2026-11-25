package lk.finora.api.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** T03: migrations apply cleanly and the schema enforces its key constraints. Each test rolls back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaMigrationTest {

    private static final List<String> TABLES = List.of(
            "users", "consents", "refresh_tokens",
            "accounts", "categories", "transactions",
            "budgets", "bills", "bill_payments", "goals", "goal_contributions",
            "insights", "health_scores", "advice", "advice_ratings",
            "device_tokens", "sms_formats", "support_tickets", "ticket_replies", "audit_events");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrationsAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSizeGreaterThanOrEqualTo(6);
        assertThat(applied).allMatch(m -> m.getState() == MigrationState.SUCCESS);
    }

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).containsAll(TABLES);
    }

    @Test
    void defaultCategoriesSeededWithSinhalaNames() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE user_id IS NULL AND name_si IS NOT NULL", Integer.class);
        assertThat(count).isEqualTo(17);
        String si = jdbc.queryForObject(
                "SELECT name_si FROM categories WHERE user_id IS NULL AND name = 'Salary'", String.class);
        assertThat(si).isEqualTo("වැටුප");
    }

    @Test
    void defaultCategorySemanticsAreUniqueAndRestrictedToDefaultExpenses() {
        assertThat(jdbc.queryForList("SELECT semantic_code FROM categories WHERE semantic_code IS NOT NULL ORDER BY id",
                String.class)).containsExactly("LOAN_PAYMENT", "SAVINGS");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO categories (name,kind,semantic_code) VALUES ('Synthetic duplicate','EXPENSE','SAVINGS')
                """));
        assertCheckViolated("ck_categories_semantic_scope", () -> jdbc.update("""
                INSERT INTO categories (name,kind,semantic_code) VALUES ('Synthetic invalid','EXPENSE','UNKNOWN')
                """));
        long user = insertUser(uniqueEmail());
        assertCheckViolated("ck_categories_semantic_scope", () -> jdbc.update("""
                UPDATE categories SET user_id = ? WHERE semantic_code = 'SAVINGS'
                """, user));
    }

    @Test
    void duplicateEmailRejected() {
        String email = uniqueEmail();
        insertUser(email);
        assertThrows(DataIntegrityViolationException.class, () -> insertUser(email));
    }

    @Test
    void duplicateBudgetForSameUserCategoryMonthRejected() {
        long user = insertUser(uniqueEmail());
        long category = defaultExpenseCategory();
        String sql = "INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-12', 15000.00)";
        jdbc.update(sql, user, category);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, user, category));
    }

    @Test
    void duplicateAdviceRatingRejected() {
        long user = insertUser(uniqueEmail());
        long advice = insert("INSERT INTO advice (user_id, month, source, mode, body) VALUES (?, '2026-12', 'RULE', 'V1', '{}')", user);
        String sql = "INSERT INTO advice_ratings (advice_id, user_id, rating) VALUES (?, ?, 4)";
        jdbc.update(sql, advice, user);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, advice, user));
    }

    @Test
    void checkConstraintsRejectInvalidValues() {
        long user = insertUser(uniqueEmail());
        long account = insert("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Wallet', 'CASH')", user);
        assertCheckViolated("ck_transactions_amount_positive", () -> jdbc.update(
                "INSERT INTO transactions (user_id, account_id, type, amount, txn_date) VALUES (?, ?, 'EXPENSE', 0.00, '2026-12-01')",
                user, account));
        assertCheckViolated("ck_budgets_month", () -> jdbc.update(
                "INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-13', 100.00)",
                user, defaultExpenseCategory()));
        long advice = insert("INSERT INTO advice (user_id, month, source, mode, body) VALUES (?, '2026-12', 'AI', 'BETA', '{}')", user);
        assertCheckViolated("ck_advice_ratings_rating", () -> jdbc.update(
                "INSERT INTO advice_ratings (advice_id, user_id, rating) VALUES (?, ?, 6)", advice, user));
        assertCheckViolated("ck_bills_due_day", () -> jdbc.update(
                "INSERT INTO bills (user_id, name, amount, due_day) VALUES (?, 'Rent', 20000.00, 32)", user));
    }

    @Test
    void moneyStoredExactlyToTheCent() {
        long user = insertUser(uniqueEmail());
        long account = insert("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Bank', 'BANK')", user);
        jdbc.update("INSERT INTO transactions (user_id, account_id, type, amount, txn_date) VALUES (?, ?, 'EXPENSE', 0.10, '2026-12-01')", user, account);
        jdbc.update("INSERT INTO transactions (user_id, account_id, type, amount, txn_date) VALUES (?, ?, 'EXPENSE', 0.20, '2026-12-01')", user, account);
        String total = jdbc.queryForObject("SELECT CAST(SUM(amount) AS CHAR) FROM transactions WHERE user_id = ?", String.class, user);
        assertThat(total).isEqualTo("0.30");
    }

    @Test
    void deletingCategoryKeepsTransactions() {
        long user = insertUser(uniqueEmail());
        long account = insert("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Bank', 'BANK')", user);
        long category = insert("INSERT INTO categories (user_id, name, kind) VALUES (?, 'Snacks', 'EXPENSE')", user);
        long txn = insert("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) VALUES (?, ?, ?, 'EXPENSE', 250.00, '2026-12-02')",
                user, account, category);
        jdbc.update("DELETE FROM categories WHERE id = ?", category);
        Long categoryId = jdbc.queryForObject("SELECT category_id FROM transactions WHERE id = ?", Long.class, txn);
        assertThat(categoryId).isNull();
    }

    @Test
    void deletingUserCascadesAllOwnedRowsButKeepsAuditEvents() {
        long user = insertUser(uniqueEmail());
        long staff = insertUser(uniqueEmail());
        jdbc.update("INSERT INTO consents (user_id, version, over18) VALUES (?, '1.0', TRUE)", user);
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, '2030-01-01')", user, sha("rt" + user));
        long account = insert("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Bank', 'BANK')", user);
        long category = insert("INSERT INTO categories (user_id, name, kind) VALUES (?, 'Snacks', 'EXPENSE')", user);
        jdbc.update("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) VALUES (?, ?, ?, 'EXPENSE', 500.00, '2026-12-03')",
                user, account, category);
        jdbc.update("INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-12', 5000.00)", user, category);
        long bill = insert("INSERT INTO bills (user_id, name, amount, due_day) VALUES (?, 'Phone', 1500.00, 5)", user);
        jdbc.update("INSERT INTO bill_payments (bill_id, month) VALUES (?, '2026-12')", bill);
        long goal = insert("INSERT INTO goals (user_id, name, target_amount) VALUES (?, 'Laptop', 200000.00)", user);
        jdbc.update("INSERT INTO goal_contributions (goal_id, amount, contrib_date) VALUES (?, 10000.00, '2026-12-04')", goal);
        jdbc.update("INSERT INTO insights (user_id, month, type, facts) VALUES (?, '2026-12', 'HIGH_WANTS', '{}')", user);
        jdbc.update("INSERT INTO health_scores (user_id, month, total, parts) VALUES (?, '2026-12', 62, '{}')", user);
        long advice = insert("INSERT INTO advice (user_id, month, source, mode, body) VALUES (?, '2026-12', 'RULE', 'V1', '{}')", user);
        jdbc.update("INSERT INTO advice_ratings (advice_id, user_id, rating) VALUES (?, ?, 5)", advice, user);
        jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", user, "tok-" + user);
        long ticket = insert("INSERT INTO support_tickets (user_id, staff_id, subject, body) VALUES (?, ?, 'Help', 'Login issue')", user, staff);
        jdbc.update("INSERT INTO ticket_replies (ticket_id, author_id, body) VALUES (?, ?, 'Try again')", ticket, user);
        long audit = insert("INSERT INTO audit_events (actor_id, action) VALUES (?, 'CONSENT_GIVEN')", user);

        jdbc.update("DELETE FROM users WHERE id = ?", user);

        for (String table : List.of("consents", "refresh_tokens", "accounts", "categories", "transactions", "budgets",
                "bills", "goals", "insights", "health_scores", "advice", "advice_ratings", "device_tokens", "support_tickets")) {
            Integer left = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, user);
            assertThat(left).as(table).isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM bill_payments WHERE bill_id = ?", bill)).isZero();
        assertThat(count("SELECT COUNT(*) FROM goal_contributions WHERE goal_id = ?", goal)).isZero();
        assertThat(count("SELECT COUNT(*) FROM ticket_replies WHERE ticket_id = ?", ticket)).isZero();
        // audit trail survives, without a link to the deleted user
        Long actor = jdbc.queryForObject("SELECT actor_id FROM audit_events WHERE id = ?", Long.class, audit);
        assertThat(actor).isNull();
        // other users are untouched
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", staff)).isEqualTo(1);
    }

    @Test
    void deletingStaffKeepsUsersTicket() {
        long user = insertUser(uniqueEmail());
        long staff = insertUser(uniqueEmail());
        long ticket = insert("INSERT INTO support_tickets (user_id, staff_id, subject, body) VALUES (?, ?, 'Help', 'SMS import')", user, staff);
        jdbc.update("DELETE FROM users WHERE id = ?", staff);
        Long staffId = jdbc.queryForObject("SELECT staff_id FROM support_tickets WHERE id = ?", Long.class, ticket);
        assertThat(staffId).isNull();
    }

    // --- helpers ---

    /** MySQL reports CHECK failures as error 3819, which Spring does not map to DataIntegrityViolationException. */
    private static void assertCheckViolated(String constraint, org.junit.jupiter.api.function.Executable action) {
        DataAccessException ex = assertThrows(DataAccessException.class, action);
        assertThat(ex.getMostSpecificCause().getMessage()).contains(constraint);
    }

    private long insertUser(String email) {
        return insert("INSERT INTO users (email, password_hash, full_name) VALUES (?, 'x', 'Test User')", email);
    }

    private long insert(String sql, Object... args) {
        jdbc.update(sql, args);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long defaultExpenseCategory() {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);
    }

    private int count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    private static String uniqueEmail() {
        return "t03-" + UUID.randomUUID() + "@test.finora.lk";
    }

    private static String sha(String s) {
        return String.format("%064x", new java.math.BigInteger(1, s.getBytes()));
    }
}
