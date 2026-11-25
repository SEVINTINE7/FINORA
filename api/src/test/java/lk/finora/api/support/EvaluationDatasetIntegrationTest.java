package lk.finora.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;

import lk.finora.api.budget.BudgetService;
import lk.finora.api.goal.GoalService;
import lk.finora.api.score.ScoreService;
import lk.finora.api.user.Role;

/** Seeded financial content is rolled back by IntegrationTest; never starts an application seeding hook. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class EvaluationDatasetIntegrationTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BudgetService budgets;
    @Autowired private GoalService goals;
    @Autowired private ScoreService scores;

    @AfterTransaction
    void seedLeavesNoPersistentFixtureUsers() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email IN (?,?)", Integer.class,
                "finora-t55-v1-worker@example.invalid", "finora-t55-v1-irregular@example.invalid")).isZero();
    }

    @Test
    void databaseMatchesManifestAndBudgetGoalMissingDataContracts() {
        var loaded = new EvaluationDatasetSeeder(jdbc).seed();
        assertThat(count("transactions", loaded)).isEqualTo(826);
        assertThat(count("budgets", loaded)).isEqualTo(24);
        assertThat(count("bills", loaded)).isEqualTo(2);
        assertThat(count("goals", loaded)).isEqualTo(2);
        assertThat(count("score_inputs", loaded)).isEqualTo(21);
        for (String profile : EvaluationDataset.PROFILES) {
            long user = loaded.users().get(profile);
            boolean worker = profile.equals("worker");
            for (String type : new String[] {"INCOME", "EXPENSE"}) {
                var total = jdbc.queryForObject("SELECT SUM(amount) FROM transactions WHERE user_id=? AND type=?", java.math.BigDecimal.class, user, type);
                String expected = worker ? (type.equals("INCOME") ? "720000" : "506099.99")
                        : (type.equals("INCOME") ? "235250" : "117099.99");
                assertThat(total).isEqualByComparingTo(expected);
            }
            var projected = jdbc.query("SELECT t.txn_date,t.type,c.name,t.amount FROM transactions t "
                    + "LEFT JOIN categories c ON c.id=t.category_id WHERE t.user_id=? ORDER BY t.id",
                    (rs, row) -> new EvaluationDataset.Entry(profile, rs.getDate(1).toLocalDate(), rs.getString(2),
                            rs.getString(3), rs.getBigDecimal(4)), user);
            assertThat(projected).isEqualTo(EvaluationDataset.entries().stream().filter(e -> e.profile().equals(profile)).toList());
            Set<String> states = new HashSet<>();
            for (int i = 0; i < 12; i++) states.add(budgets.list(user, EvaluationDataset.START.plusMonths(i).toString()).get(0).state());
            assertThat(states).containsExactlyInAnyOrder("OK", "NEAR", "OVER");
            var goal = goals.get(user, loaded.goals().get(profile));
            assertThat(goal.saved()).isEqualByComparingTo(worker ? "24000" : "3000");
            assertThat(goal.progressPercent()).isEqualTo(worker ? 100 : 30);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_contributions WHERE goal_id=?", Integer.class, goal.id())).isEqualTo(worker ? 12 : 6);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.user_id=?", Integer.class, user)).isEqualTo(worker ? 12 : 6);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id=t.account_id WHERE t.user_id=? AND a.user_id<>t.user_id", Integer.class, user)).isZero();
        }
        long irregular = loaded.users().get("irregular");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id=? AND txn_date BETWEEN '2026-09-01' AND '2026-09-30'", Integer.class, irregular)).isZero();
        assertThat(scores.forMonth(irregular, "2026-09").total()).isNull();
        assertThat(scores.getInputs(irregular, "2025-12").confirmedAt()).isNull();
        assertThat(scores.getInputs(irregular, "2026-05").emergencyReserve()).isNull();
        assertThat(scores.getInputs(loaded.users().get("worker"), "2025-12").emergencyReserve()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT DATE_FORMAT(txn_date,'%Y-%m')) FROM transactions WHERE user_id IN (?,?)", Integer.class,
                loaded.users().get("worker"), irregular)).isEqualTo(12);
    }

    private int count(String table, EvaluationDatasetSeeder.Loaded loaded) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id IN (?,?)", Integer.class,
                loaded.users().get("worker"), loaded.users().get("irregular"));
    }

    @Test
    void ownershipExistingRowsAndDuplicateSeedAreProtected() throws Exception {
        var control = createUser(Role.USER);
        jdbc.update("INSERT INTO accounts(user_id,name,type,opening_balance) VALUES (?,'SYNTHETIC untouched control','CASH',321.09)", control.getId());
        var categoriesBefore = jdbc.queryForList("SELECT * FROM categories ORDER BY id");
        var controlBefore = jdbc.queryForList("SELECT * FROM accounts WHERE user_id=?", control.getId());
        var loaded = new EvaluationDatasetSeeder(jdbc).seed();
        for (String profile : EvaluationDataset.PROFILES) {
            var user = userRepository.findById(loaded.users().get(profile)).orElseThrow();
            String body = mockMvc.perform(get("/api/v1/dashboard?month=2026-11").header("Authorization", "Bearer " + tokenFor(user)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var result = jsonMapper.readTree(body);
            assertThat(result.get("income").decimalValue()).isEqualByComparingTo(profile.equals("worker") ? "60000" : "25750");
            assertThat(result.get("expense").decimalValue()).isEqualByComparingTo(profile.equals("worker") ? "40000" : "11000");
        }
        String controlDashboard = mockMvc.perform(get("/api/v1/dashboard?month=2026-11").header("Authorization", "Bearer " + tokenFor(control)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jsonMapper.readTree(controlDashboard).get("income").decimalValue()).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM categories ORDER BY id")).isEqualTo(categoriesBefore);
        assertThat(jdbc.queryForList("SELECT * FROM accounts WHERE user_id=?", control.getId())).isEqualTo(controlBefore);
        assertThatThrownBy(() -> new EvaluationDatasetSeeder(jdbc).seed()).isInstanceOf(IllegalStateException.class).hasMessageContaining("already exist");
        assertThat(count("transactions", loaded)).isEqualTo(826);
    }
}
