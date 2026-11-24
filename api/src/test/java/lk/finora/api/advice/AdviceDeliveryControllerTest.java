package lk.finora.api.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdviceDeliveryControllerTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdviceDeliveryRepository repository;

    @Test void deliveryAndRatingRequireTheUserRole() throws Exception {
        String endpoint = "/api/v1/advice?month=2026-11&mode=V1";
        mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.ADMIN, Role.STAFF}) {
            String auth = "Bearer " + tokenFor(createUser(role));
            mockMvc.perform(get(endpoint).header("Authorization", auth)).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/advice/1/rating").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingRatingsCommitExactlyOneValue() throws Exception {
        var owner = createUser(Role.USER);
        var threads = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var exact = new java.math.BigDecimal("9007199254740993.21");
            var candidate = new AdvicePipelineService.Delivery("RULE", false, "TEST",
                    java.util.List.of(new lk.finora.api.advice.ai.AdviceItem("REVIEW_SPENDING_DEFICIT",
                            "Synthetic test fixture", java.util.List.of(new lk.finora.api.advice.ai.AdviceItem.NumberFact(
                            lk.finora.api.advice.ai.FactKey.SPENDING_DEFICIT, exact, "LKR", "2026-11")))));
            long id = repository.publish(owner.getId(), "2026-11", candidate).id();
            org.junit.jupiter.api.Assertions.assertEquals(exact,
                    repository.find(owner.getId(), "2026-11").orElseThrow().items().get(0).numbers().get(0).value());
            var gate = new java.util.concurrent.CyclicBarrier(2);
            String auth = "Bearer " + tokenFor(owner);
            java.util.function.Function<Integer, java.util.concurrent.Callable<Integer>> request = value -> () -> {
                gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/advice/" + id + "/rating").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":" + value + "}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = threads.submit(request.apply(1));
            var second = threads.submit(request.apply(5));
            org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(201, 409), java.util.Set.of(
                    first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM advice_ratings WHERE advice_id = ?", Integer.class, id));
        } finally {
            threads.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        }
    }

    @Test void publishesCanonicalRulesAndReconcilesOneRating() throws Exception {
        var owner = createUser(Role.USER);
        String auth = "Bearer " + tokenFor(owner);
        String foreign = "Bearer " + tokenFor(createUser(Role.USER));
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'Advice wallet','CASH')", owner.getId());
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, owner.getId());
        jdbc.update("""
                INSERT INTO transactions(user_id,account_id,type,amount,txn_date)
                VALUES (?,?,'INCOME',100.00,'2026-11-02'), (?,?,'EXPENSE',150.23,'2026-11-03')
                """, owner.getId(), account, owner.getId(), account);
        String monthly = "/api/v1/advice?month=2026-11&mode=V1";
        var first = mockMvc.perform(get(monthly).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE")).andExpect(jsonPath("$.items[0].numbers").isArray())
                .andReturn();
        long id = jsonMapper.readTree(first.getResponse().getContentAsString()).path("id").longValue();
        String rating = "/api/v1/advice/" + id + "/rating";
        mockMvc.perform(post(rating).header("Authorization", foreign).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isNotFound());
        mockMvc.perform(post(rating).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4.0}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(rating).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isCreated());
        mockMvc.perform(post(rating).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":1}")).andExpect(status().isConflict());
        mockMvc.perform(get(monthly).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.rating").value(5));
        mockMvc.perform(get(monthly).header("Authorization", foreign)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.id").isEmpty());
    }
}
