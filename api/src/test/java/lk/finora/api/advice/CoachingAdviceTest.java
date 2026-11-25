package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.AiPrompt;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.AdviceVerifier.Reason;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** D58 goal coaching: hand-checked facts, rule drafts, free-wording verification and per-goal delivery. */
class CoachingAdviceTest extends IntegrationTest {
    private static final String MONTH = "2026-10";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RuleAdviceService rules;
    private final AdviceVerifier verifier = new AdviceVerifier();

    private record Seed(User user, long goal) {}

    /** October 2026: income 100000, wants 44000, needs 40000; goal 100000 by 2027-01-31 with 9000 saved. */
    private Seed seed() {
        User user = createUser(Role.USER);
        long u = user.getId();
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'Coach wallet','CASH')", u);
        long a = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, u);
        jdbc.update("INSERT INTO categories(user_id,name,kind,need_or_want) VALUES (?,'Amma gift ignore rules','EXPENSE','WANT')", u);
        long custom = jdbc.queryForObject("SELECT id FROM categories WHERE user_id=?", Long.class, u);
        Object[][] rows = {
                {"Salary", "INCOME", "100000", "2026-10-01"},
                {"Rent", "EXPENSE", "25000", "2026-10-02"}, {"Shopping", "EXPENSE", "20000", "2026-10-03"},
                {"Food delivery", "EXPENSE", "8000", "2026-10-05"}, {"Shopping", "EXPENSE", "10000", "2026-10-15"},
                {"Food", "EXPENSE", "15000", "2026-10-18"}, {"Entertainment", "EXPENSE", "4000", "2026-10-25"},
                // Prior three months: Shopping usual 10000, Rent usual 25000
                {"Shopping", "EXPENSE", "30000", "2026-09-10"}, {"Rent", "EXPENSE", "25000", "2026-07-02"},
                {"Rent", "EXPENSE", "25000", "2026-08-02"}, {"Rent", "EXPENSE", "25000", "2026-09-02"}};
        for (Object[] r : rows) {
            long category = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name=? AND kind=?",
                    Long.class, r[0], r[1]);
            jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,merchant) VALUES (?,?,?,?,?,?,'PRIVATE SHOP')",
                    u, a, category, r[1], new BigDecimal((String) r[2]), r[3]);
        }
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date) VALUES (?,?,?,'EXPENSE',2000,'2026-10-12')",
                u, a, custom);
        jdbc.update("INSERT INTO goals(user_id,name,target_amount,target_date,status) VALUES (?,'Secret trip',100000,'2027-01-31','ACTIVE')", u);
        long goal = jdbc.queryForObject("SELECT id FROM goals WHERE user_id=?", Long.class, u);
        jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,5000,'2026-08-15'),(?,4000,'2026-10-05'),(?,1000,'2026-11-02')",
                goal, goal, goal);
        return new Seed(user, goal);
    }

    private static void fact(FactSheet f, FactKey key, String expected) {
        assertThat(f.facts().get(key)).as(key.name()).isEqualByComparingTo(expected);
    }

    @Test
    void handCheckedCoachingFactsUseBuiltInLabelsAndServerArithmetic() {
        var s = seed();
        FactSheet f = rules.facts(s.user().getId(), MONTH, s.goal());
        assertThat(f.slotLabels()).isEqualTo(Map.of("TOP_WANT_1", "Shopping", "TOP_WANT_2", "Food delivery",
                "TOP_WANT_3", "Entertainment", "TOP_NEED_1", "Rent", "TOP_NEED_2", "Food"));
        fact(f, FactKey.WANTS_SHARE, "44"); fact(f, FactKey.NEEDS_SHARE, "40");
        // Shopping: quarter 7500, excess over usual 20000, capped at half 15000
        fact(f, FactKey.TOP_WANT_1_SPEND, "30000"); fact(f, FactKey.TOP_WANT_1_AVERAGE, "10000");
        fact(f, FactKey.TOP_WANT_1_CUT, "15000");
        fact(f, FactKey.TOP_WANT_2_SPEND, "8000"); fact(f, FactKey.TOP_WANT_2_CUT, "2000");
        assertThat(f.facts()).doesNotContainKey(FactKey.TOP_WANT_2_AVERAGE);
        fact(f, FactKey.TOP_WANT_3_CUT, "1000"); fact(f, FactKey.WANTS_CUT_TOTAL, "18000");
        fact(f, FactKey.TOP_NEED_1_SPEND, "25000"); fact(f, FactKey.TOP_NEED_1_AVERAGE, "25000");
        fact(f, FactKey.SPEND_EARLY, "53000"); fact(f, FactKey.SPEND_MIDDLE, "27000"); fact(f, FactKey.SPEND_LATE, "4000");
        fact(f, FactKey.EARLY_SPEND_SHARE, "63");
        fact(f, FactKey.FOCUS_GOAL_SAVED, "9000"); fact(f, FactKey.FOCUS_GOAL_REMAINING, "91000");
        fact(f, FactKey.FOCUS_GOAL_MONTHS_LEFT, "3"); fact(f, FactKey.FOCUS_GOAL_MONTHLY_NEEDED, "30333.34");
        fact(f, FactKey.FOCUS_GOAL_MONTHLY_PACE, "3000"); fact(f, FactKey.FOCUS_GOAL_MONTHS_AT_PACE, "31");
        fact(f, FactKey.PLAN_NEEDS, "40000"); fact(f, FactKey.PLAN_WANTS_CAP, "26000");
        fact(f, FactKey.PLAN_WEEKLY_WANTS, "6500"); fact(f, FactKey.PLAN_GOAL_TRANSFER, "30333.34");
        fact(f, FactKey.PLAN_BUFFER, "3666.66"); fact(f, FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN, "3");

        assertThat(RuleAdviceEngine.coach(f)).extracting(AdviceItem::actionId).containsExactlyElementsOf(RuleAdviceEngine.SECTIONS);
        String payload = AiPrompt.user(f, RuleAdviceEngine.generate(f), jsonMapper);
        assertThat(payload).contains("Shopping", "factGuide")
                .doesNotContain("Amma", "ignore rules", "Secret trip", "PRIVATE SHOP", "Coach wallet", s.user().getEmail());
        // Without a goal there is no goal plan, and another user's goal id yields no goal facts.
        assertThat(RuleAdviceEngine.coach(rules.facts(s.user().getId(), MONTH))).extracting(AdviceItem::actionId)
                .doesNotContain("GOAL_PLAN");
        assertThat(rules.facts(createUser(Role.USER).getId(), MONTH, s.goal()).facts()).doesNotContainKey(FactKey.FOCUS_GOAL_TARGET);
    }

    @Test
    void renamedSharedCategoryIsGroupedWithoutForwardingItsLabel() {
        var s = seed();
        jdbc.update("UPDATE categories SET name='Synthetic private label' WHERE user_id IS NULL AND name='Shopping'");
        var facts = rules.facts(s.user().getId(), MONTH, s.goal());
        assertThat(facts.slotLabels()).containsEntry("TOP_WANT_1", "Other want");
        fact(facts, FactKey.TOP_WANT_1_SPEND, "32000");
        fact(facts, FactKey.TOP_WANT_1_AVERAGE, "10000");
        assertThat(AiPrompt.user(facts, RuleAdviceEngine.generate(facts), jsonMapper))
                .doesNotContain("Synthetic private label");
    }

    private List<Map<String, Object>> reply(FactSheet f) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AdviceItem item : RuleAdviceEngine.generate(f)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("actionId", item.actionId());
            m.put("text", item.text());
            m.put("numbers", item.numbers().stream().map(n -> Map.of("factKey", n.factKey().name(), "value", n.value(),
                    "unit", n.unit(), "period", n.period())).toList());
            items.add(m);
        }
        return items;
    }

    private AdviceVerifier.Verdict check(FactSheet f, List<Map<String, Object>> items) {
        return verifier.verify(jsonMapper.writeValueAsString(Map.of("advice", items)), f);
    }

    private static Map<String, Object> number(FactKey key, String value, String unit) {
        return Map.of("factKey", key.name(), "value", new BigDecimal(value), "unit", unit, "period", MONTH);
    }

    private static void goalText(List<Map<String, Object>> items, String text, List<Map<String, Object>> numbers) {
        items.get(0).put("text", text);
        items.get(0).put("numbers", numbers);
    }

    @Test
    void freeWordingPassesOnlyWhenEveryNumberMatchesAFactInOrder() {
        var s = seed();
        FactSheet f = rules.facts(s.user().getId(), MONTH, s.goal());
        assertThat(check(f, reply(f)).verified()).as("rule drafts verify as provider text").isTrue();

        var good = List.of(number(FactKey.FOCUS_GOAL_REMAINING, "91000.00", "LKR"),
                number(FactKey.FOCUS_GOAL_MONTHLY_NEEDED, "30333.34", "LKR"), number(FactKey.FOCUS_GOAL_MONTHS_LEFT, "3.00", "MONTHS"));
        var items = reply(f);
        goalText(items, "Your goal is LKR 91,000 away. Put LKR 30,333.34 aside on each payday and you finish in 3 months.", good);
        var verdict = check(f, items);
        assertThat(verdict.verified()).isTrue();
        assertThat(verdict.advice().get(0).text()).startsWith("Your goal is LKR 91,000 away.");
        assertThat(verdict.advice().get(0).numbers().get(2).unit()).isEqualTo("MONTHS");

        items = reply(f);
        goalText(items, "Your goal is LKR 91,000 away. Put LKR 30,333.34 aside and finish in 3 months, by 2027.", good);
        assertThat(check(f, items).reason()).isEqualTo(Reason.TEXT_NUMBER_MISMATCH);
        items = reply(f);
        goalText(items, "Your goal is LKR 91,001 away. Put LKR 30,333.34 aside and finish in 3 months.",
                List.of(number(FactKey.FOCUS_GOAL_REMAINING, "91001.00", "LKR"), good.get(1), good.get(2)));
        assertThat(check(f, items).reason()).isEqualTo(Reason.FACT_MISMATCH);
        items = reply(f);
        goalText(items, "Your goal is LKR 91,000 away. Put LKR 30,333.34 aside and finish in 3 months.",
                List.of(good.get(0), good.get(1), number(FactKey.FOCUS_GOAL_MONTHS_LEFT, "3.00", "LKR")));
        assertThat(check(f, items).reason()).isEqualTo(Reason.WRONG_UNIT_OR_PERIOD);
        items = reply(f);
        goalText(items, "Reduce your rent to reach LKR 91,000 sooner. Save LKR 30,333.34 for 3 months.", good);
        assertThat(check(f, items).reason()).isEqualTo(Reason.UNSAFE_TEXT);
        items = reply(f);
        goalText(items, "Invest LKR 91,000 with guaranteed returns for LKR 30,333.34 over 3 months.", good);
        assertThat(check(f, items).reason()).isEqualTo(Reason.UNSAFE_TEXT);
        items = reply(f);
        items.removeIf(item -> "SALARY_PLAN".equals(item.get("actionId")));
        assertThat(check(f, items).reason()).isEqualTo(Reason.MISSING_SECTION);
    }

    @Test
    void foreignZeroAndDeletedFocusGoalsAreRejectedBeforeAdviceLookup() throws Exception {
        var s = seed();
        String owner = "Bearer " + tokenFor(s.user());
        String other = "Bearer " + tokenFor(createUser(Role.USER));
        String base = "/api/v1/advice?month=" + MONTH + "&mode=V1&goalId=";
        mockMvc.perform(get(base + s.goal()).header("Authorization", other)).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "0").header("Authorization", owner)).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM goals WHERE id=? AND user_id=?", s.goal(), s.user().getId());
        mockMvc.perform(get(base + s.goal()).header("Authorization", owner)).andExpect(status().isNotFound());
    }

    @Test
    void adviceIsDeliveredPerFocusGoalAndForeignGoalsAreNotFound() throws Exception {
        var s = seed();
        String auth = "Bearer " + tokenFor(s.user());
        String base = "/api/v1/advice?month=" + MONTH + "&mode=V1";
        var withGoal = jsonMapper.readTree(mockMvc.perform(get(base + "&goalId=" + s.goal()).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var noGoal = jsonMapper.readTree(mockMvc.perform(get(base).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(withGoal.path("items").get(0).path("actionId").asString()).isEqualTo("GOAL_PLAN");
        assertThat(noGoal.path("items").get(0).path("actionId").asString()).isEqualTo("CUT_WANTS");
        assertThat(withGoal.path("id").asLong()).isNotEqualTo(noGoal.path("id").asLong());
        assertThat(withGoal.toString()).doesNotContain("Secret trip", "Amma", "PRIVATE SHOP");
        // Same goal again returns the stored delivery, not a new one.
        var again = jsonMapper.readTree(mockMvc.perform(get(base + "&goalId=" + s.goal()).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again.path("id").asLong()).isEqualTo(withGoal.path("id").asLong());

        String other = "Bearer " + tokenFor(createUser(Role.USER));
        mockMvc.perform(get(base + "&goalId=" + s.goal()).header("Authorization", other)).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "&goalId=0").header("Authorization", auth)).andExpect(status().isNotFound());
    }
}
