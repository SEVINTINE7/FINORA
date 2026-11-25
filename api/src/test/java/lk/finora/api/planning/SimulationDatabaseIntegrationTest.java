package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** T32: real database-backed simulation and financial non-mutation checks. */
class SimulationDatabaseIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions send(
            User user,
            MockHttpServletRequestBuilder request,
            Object body) throws Exception {

        request.header("Authorization", "Bearer " + tokenFor(user));
        request.contentType(MediaType.APPLICATION_JSON);

        if (body != null) {
            request.content(json(body));
        }

        return mockMvc.perform(request);
    }

    private Long createId(User user, String path, Object body)
            throws Exception {

        String response = send(user, post(path), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(response).get("id").asLong();
    }

    private record Fixture(Long goalId, Long categoryId) {
    }

    private Fixture prepare(User user) throws Exception {

        Long goalId = createId(
                user,
                "/api/v1/goals",
                Map.of(
                        "name", "Laptop savings",
                        "targetAmount", "10000.00"));

        Long accountId = createId(
                user,
                "/api/v1/accounts",
                Map.of(
                        "name", "Test bank account",
                        "type", "BANK",
                        "openingBalance", "10000.00"));

        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories "
                + "WHERE user_id IS NULL AND active = 1 "
                + "AND kind = 'EXPENSE' "
                + "ORDER BY id LIMIT 1",
                Long.class);

        LocalDate previousMonth = LocalDate
                .now(ZoneId.of("Asia/Colombo"))
                .minusMonths(1)
                .withDayOfMonth(15);

        send(user, post("/api/v1/transactions"), Map.of(
                "accountId", accountId,
                "categoryId", categoryId,
                "type", "EXPENSE",
                "amount", "600.00",
                "txnDate", previousMonth.toString()))
                .andExpect(status().isCreated());

        return new Fixture(goalId, categoryId);
    }

    private Map<String, Object> scenario(
            Fixture fixture, String reduction) {

        return Map.of(
                "goalId", fixture.goalId(),
                "baselineMonthlySavings", "1000.00",
                "monthlyChange", "-100.00",
                "categoryCuts", List.of(Map.of(
                        "categoryId", fixture.categoryId(),
                        "monthlyReduction", reduction)));
    }

    @Test
    void validReductionUsesRealRecordedExpenseAndChangesNothing()
            throws Exception {

        User owner = createUser(Role.USER);
        Fixture fixture = prepare(owner);

        var goalsBefore = jdbc.queryForList(
                "SELECT * FROM goals WHERE user_id = ? ORDER BY id",
                owner.getId());

        var accountsBefore = jdbc.queryForList(
                "SELECT * FROM accounts WHERE user_id = ? ORDER BY id",
                owner.getId());

        var transactionsBefore = jdbc.queryForList(
                "SELECT * FROM transactions WHERE user_id = ? ORDER BY id",
                owner.getId());

        send(owner, post("/api/v1/simulate"),
                scenario(fixture, "250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryReductions").value(250.00))
                .andExpect(jsonPath("$.hypotheticalMonthlySavings").value(1150.00))
                .andExpect(jsonPath("$.remaining").value(10000.00))
                .andExpect(jsonPath("$.monthsRequired").value(9))
                .andExpect(jsonPath("$.status").value("POSSIBLE"));

        assertThat(jdbc.queryForList(
                "SELECT * FROM goals WHERE user_id = ? ORDER BY id",
                owner.getId())).isEqualTo(goalsBefore);

        assertThat(jdbc.queryForList(
                "SELECT * FROM accounts WHERE user_id = ? ORDER BY id",
                owner.getId())).isEqualTo(accountsBefore);

        assertThat(jdbc.queryForList(
                "SELECT * FROM transactions WHERE user_id = ? ORDER BY id",
                owner.getId())).isEqualTo(transactionsBefore);
    }

    @Test
    void exactRecordedExpenseReductionIsAccepted()
            throws Exception {

        User owner = createUser(Role.USER);
        Fixture fixture = prepare(owner);

        send(owner, post("/api/v1/simulate"),
                scenario(fixture, "600.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryReductions").value(600.00))
                .andExpect(jsonPath("$.hypotheticalMonthlySavings").value(1500.00))
                .andExpect(jsonPath("$.monthsRequired").value(7));
    }

    @Test
    void duplicateCategoryCutsAreRejectedThroughHttp()
            throws Exception {

        User owner = createUser(Role.USER);
        Fixture fixture = prepare(owner);

        Map<String, Object> reduction = Map.of(
                "categoryId", fixture.categoryId(),
                "monthlyReduction", "100.00");

        send(owner, post("/api/v1/simulate"), Map.of(
                "goalId", fixture.goalId(),
                "baselineMonthlySavings", "1000.00",
                "monthlyChange", "0.00",
                "categoryCuts", List.of(reduction, reduction)))
                .andExpect(status().isBadRequest());
    }
}