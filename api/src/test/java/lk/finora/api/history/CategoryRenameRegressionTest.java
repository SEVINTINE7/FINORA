package lk.finora.api.history;

import java.time.YearMonth;
import java.util.Map;
import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.analytics.MetricsService;
import lk.finora.api.score.ScoreService;
import lk.finora.api.advice.ai.FactSheetFactory;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** D55 regression suite, included in normal Maven verification. */
class CategoryRenameRegressionTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired MetricsService metrics;
    @Autowired ScoreService scores;
    @Autowired HistoryService history;
    static final String MONTH = "2026-10";
    private record Fixture(long user, long savings, long loan, String admin, String owner) {}
    private long category(String name) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name=?",Long.class,name);
    }
    private Fixture seed(boolean bill) {
        var user = createUser(Role.USER);
        var admin = createUser(Role.ADMIN);
        entities.flush();
        long id = user.getId(), savings = category("Savings"), loan = category("Loan payment");
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'Semantic fixture','CASH')",id);
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?",Long.class,id);
        for (String[] row : new String[][]{{"INCOME","100000.00","Salary"},{"EXPENSE","60000.00","Food"},{"EXPENSE","30000.00","Shopping"},{"EXPENSE","5000.00","Savings"},{"EXPENSE","1000.00","Loan payment"}}) {
            jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date) VALUES (?,?,?,?,?,'2026-10-10')",id,account,category(row[2]),row[0],row[1]);
        }
        jdbc.update("INSERT INTO budgets(user_id,category_id,month,limit_amount) VALUES (?,?,'2026-10',6000)",id,savings);
        if (bill) jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id,created_at) VALUES (?,'Synthetic loan',15000,15,?,'2026-10-01')",id,loan);
        jdbc.update("INSERT INTO score_inputs(user_id,month,emergency_reserve,reserve_as_of,no_applicable_debt,no_applicable_goals,confirmed_at) VALUES (?,'2026-10',30000,'2026-10-20',FALSE,TRUE,'2026-10-20')",id);
        return new Fixture(id,savings,loan,"Bearer " + tokenFor(admin),"Bearer " + tokenFor(user));
    }
    private void rename(Fixture f,long category,String name,String sinhala) throws Exception {
        mockMvc.perform(put("/api/v1/admin/categories/" + category).header("Authorization",f.admin())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name",name,"nameSi",sinhala))))
                .andExpect(status().isOk());
        entities.flush(); entities.clear();
        assertThat(jdbc.queryForObject("SELECT name FROM categories WHERE id=?",String.class,category)).isEqualTo(name);
        assertThat(jdbc.queryForObject("SELECT name_si FROM categories WHERE id=?",String.class,category)).isEqualTo(sinhala);
    }
    private void invariance(boolean savings, boolean lifecycle) throws Exception {
        Fixture f = seed(true);
        var key = new HistoryService.Key(f.user(), MONTH);
        history.materialize(key, false);
        var beforeHistory = history.read(f.user(), MONTH, MONTH).get(0);
        assertThat(beforeHistory.score()).isEqualTo(70);
        var beforeMetrics = metrics.forMonth(f.user(),YearMonth.parse(MONTH));
        var beforeScore = scores.forMonth(f.user(),MONTH);
        var beforeFacts = FactSheetFactory.from(beforeMetrics,beforeScore);
        var beforeRules = RuleAdviceEngine.generate(beforeFacts);
        assertThat(beforeMetrics.savedToCategory().amount()).isEqualByComparingTo("5000.00");
        assertThat(beforeFacts.facts().get(FactKey.UNMARKED_LOAN_BILLS)).isEqualByComparingTo("15000.00");
        assertThat(beforeScore.total()).isNotNull();
        assertThat(beforeRules).isNotEmpty();
        if (savings) rename(f,f.savings(),"Synthetic savings renamed","පරීක්ෂණ ඉතිරි");
        else rename(f,f.loan(),"Synthetic loan renamed","පරීක්ෂණ ණය");
        if (lifecycle) {
            long original = savings ? f.savings() : f.loan();
            mockMvc.perform(delete("/api/v1/admin/categories/" + original)
                    .header("Authorization", f.admin())).andExpect(status().isNoContent());
            long decoy = createDecoy(f, savings ? "Savings" : "Loan payment");
            assertThat(decoy).isNotEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT active FROM categories WHERE id=?", Boolean.class, original)).isFalse();
            assertThat(jdbc.queryForObject("SELECT semantic_code FROM categories WHERE id=?", String.class, original))
                    .isEqualTo(savings ? "SAVINGS" : "LOAN_PAYMENT");
            entities.flush(); entities.clear();
        }
        // Exercise the real dirty-refresh and recovery paths using only this rolled-back fixture.
        jdbc.update("UPDATE monthly_history SET dirty=TRUE WHERE user_id=? AND month=?", f.user(), MONTH);
        history.materialize(key, true);
        assertThat(history.read(f.user(), MONTH, MONTH).get(0)).isEqualTo(beforeHistory);
        jdbc.update("DELETE FROM monthly_history WHERE user_id=? AND month=?", f.user(), MONTH);
        jdbc.update("UPDATE users SET created_at='2026-09-01' WHERE id=?", f.user());
        history.recover(key, java.time.LocalDateTime.parse("2026-10-31T18:30:00"));
        var recovered = history.read(f.user(), MONTH, MONTH).get(0);
        assertThat(recovered.reconstructedAt()).isNotNull();
        assertThat(recovered.score()).isEqualTo(beforeHistory.score());
        assertThat(recovered.income()).isEqualByComparingTo(beforeHistory.income());
        assertThat(recovered.expense()).isEqualByComparingTo(beforeHistory.expense());
        assertThat(recovered.insights()).isEqualTo(beforeHistory.insights());
        assertThat(recovered.refreshPending()).isFalse();
        var afterMetrics = metrics.forMonth(f.user(),YearMonth.parse(MONTH));
        var afterScore = scores.forMonth(f.user(),MONTH);
        var afterFacts = FactSheetFactory.from(afterMetrics,afterScore);
        var afterRules = RuleAdviceEngine.generate(afterFacts);
        assertAll("CATEGORY_RENAME_INVARIANCE",
                () -> assertThat(afterMetrics).isEqualTo(beforeMetrics),
                () -> assertThat(afterScore).isEqualTo(beforeScore),
                () -> assertThat(afterFacts.facts()).isEqualTo(beforeFacts.facts()),
                () -> assertThat(afterRules).isEqualTo(beforeRules),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id=? AND category_id=?",Integer.class,f.user(),f.savings())).isEqualTo(1),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM budgets WHERE user_id=? AND category_id=?",Integer.class,f.user(),f.savings())).isEqualTo(1),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE user_id=? AND category_id=?",Integer.class,f.user(),f.loan())).isEqualTo(1));
    }
    @Test void savingsDisplayRenamePreservesFinancialResults() throws Exception { invariance(true, false); }
    @Test void loanDisplayRenamePreservesFinancialResults() throws Exception { invariance(false, false); }
    @Test void savingsDeactivationAndSameNameDecoyPreserveHistory() throws Exception { invariance(true, true); }
    @Test void loanDeactivationAndSameNameDecoyPreserveHistory() throws Exception { invariance(false, true); }

    private long createDecoy(Fixture f, String name) throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories").header("Authorization", f.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "nameSi", "Synthetic " + name, "kind", "EXPENSE"))))
                .andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND kind='EXPENSE' AND name=?", Long.class, name);
        assertThat(jdbc.queryForObject("SELECT semantic_code FROM categories WHERE id=?", String.class, id)).isNull();
        return id;
    }

    @Test void ordinaryDecoysDoNotAcquireSavingsOrDebtMeaning() throws Exception {
        Fixture f = seed(true);
        rename(f, f.savings(), "Synthetic savings renamed", "Synthetic saving label");
        rename(f, f.loan(), "Synthetic loan renamed", "Synthetic debt label");
        long savingsDecoy = createDecoy(f, "Savings");
        long loanDecoy = createDecoy(f, "Loan payment");
        var other = createUser(Role.USER);
        entities.flush();
        long uid = other.getId();
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'Decoy fixture','CASH')", uid);
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, uid);
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date) VALUES (?,?,?,'EXPENSE',123.45,'2026-10-10'),(?,?,?,'EXPENSE',67.89,'2026-10-10')", uid, account, savingsDecoy, uid, account, loanDecoy);
        jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id,created_at) VALUES (?,'Ordinary bill',222.22,15,?,'2026-10-01')", uid, loanDecoy);
        entities.clear();
        assertThat(metrics.forMonth(uid, YearMonth.parse(MONTH)).savedToCategory().amount()).isEqualByComparingTo("0.00");
        mockMvc.perform(put("/api/v1/score-inputs/" + MONTH).header("Authorization", "Bearer " + tokenFor(other))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("noApplicableDebt", true))))
                .andExpect(status().isOk());
        assertThat(FactSheetFactory.from(metrics.forMonth(uid, YearMonth.parse(MONTH)), scores.forMonth(uid, MONTH)).facts().get(FactKey.UNMARKED_LOAN_BILLS))
                .isEqualByComparingTo("0.00");
    }

    @Test void loanTransactionStillContradictsNoDebtDeclarationAfterRename() throws Exception {
        Fixture f = seed(false);
        String payload = json(Map.of("noApplicableDebt",true));
        mockMvc.perform(put("/api/v1/score-inputs/" + MONTH).header("Authorization",f.owner()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        rename(f,f.loan(),"Synthetic loan renamed","පරීක්ෂණ ණය");
        mockMvc.perform(put("/api/v1/score-inputs/" + MONTH).header("Authorization",f.owner()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
    }
}
