package lk.finora.api.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdviceDeliveryControllerTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdviceDeliveryRepository repository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstReadsShareOneGenerationAndPreserveRating() throws Exception {
        long owner = createUser(Role.USER).getId();
        var pipeline = mock(AdvicePipelineService.class);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(pipeline.forCurrentUser("2026-11")).thenAnswer(call -> {
            entered.countDown();
            assertTrue(finish.await(10, TimeUnit.SECONDS));
            return fallback();
        });
        var firstInstance = new AdviceDeliveryService(repository, pipeline);
        var secondInstance = new AdviceDeliveryService(repository, pipeline);
        var thread = Executors.newSingleThreadExecutor();
        try {
            var first = thread.submit(() -> {
                authenticate(owner);
                try { return firstInstance.get("2026-11", "V1"); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            // A separate connection can acquire the user write lock while the provider is paused.
            jdbc.update("UPDATE users SET id = id WHERE id = ?", owner);
            authenticate(owner);
            assertEquals("ADVICE_PENDING", assertThrows(ApiException.class,
                    () -> secondInstance.get("2026-11", "V1")).getCode());
            finish.countDown();
            long id = first.get(10, TimeUnit.SECONDS).id();
            secondInstance.rate(id, 5);
            var delivered = secondInstance.get("2026-11", "V1");
            assertEquals(id, delivered.id());
            assertEquals(5, delivered.rating());
            verify(pipeline, times(1)).forCurrentUser("2026-11");
        } finally {
            finish.countDown();
            SecurityContextHolder.clearContext();
            thread.shutdownNow();
            thread.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
        }
    }

    @Test
    void expiredLeaseCanBeReplacedAndStaleOwnerCannotPublishOrReleaseIt() {
        long owner = createUser(Role.USER).getId();
        String first = UUID.randomUUID().toString();
        String replacement = UUID.randomUUID().toString();
        assertTrue(repository.claimGeneration(owner, "2026-11", first));
        jdbc.update("UPDATE advice_generation_leases SET expires_at = UTC_TIMESTAMP(6) WHERE user_id = ?", owner);
        assertTrue(repository.claimGeneration(owner, "2026-11", replacement));
        repository.releaseGeneration(owner, "2026-11", first);
        assertThrows(ApiException.class, () -> repository.publish(owner, "2026-11", first, fallback()));
        assertNotNull(repository.publish(owner, "2026-11", replacement, fallback()).id());
    }

    @Test
    void processingFailureReleasesClaimForImmediateRetry() {
        long owner = createUser(Role.USER).getId();
        var pipeline = mock(AdvicePipelineService.class);
        when(pipeline.forCurrentUser("2026-11"))
                .thenThrow(new IllegalStateException("Synthetic processing failure"))
                .thenReturn(fallback());
        var service = new AdviceDeliveryService(repository, pipeline);
        authenticate(owner);
        try {
            assertThrows(IllegalStateException.class, () -> service.get("2026-11", "V1"));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM advice_generation_leases WHERE user_id = ?", Integer.class, owner));
            assertEquals("RULE", service.get("2026-11", "V1").source());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void authenticate(long owner) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(owner, null, List.of()));
    }

    private static AdvicePipelineService.Delivery fallback() {
        return new AdvicePipelineService.Delivery("RULE", false, "PROVIDERS_FAILED",
                List.of(new AdviceItem("REVIEW_SPENDING_DEFICIT", "Synthetic fallback", List.of())));
    }

    @Test void legacyBetaRequestDoesNotDeliverOrPersistAdvice() throws Exception {
        var owner = createUser(Role.USER);
        mockMvc.perform(get("/api/v1/advice?month=2026-11&mode=BETA")
                .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mode must be V1"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM advice WHERE user_id=?", Integer.class, owner.getId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM advice_generation_leases WHERE user_id=?",
                Integer.class, owner.getId())).isZero();
    }

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
            String lease = java.util.UUID.randomUUID().toString();
            org.junit.jupiter.api.Assertions.assertTrue(repository.claimGeneration(owner.getId(), "2026-11", lease));
            long id = repository.publish(owner.getId(), "2026-11", lease, candidate).id();
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
        jdbc.update("UPDATE transactions SET merchant=?, note=? WHERE user_id=? AND type='EXPENSE'",
                "fixture-private-merchant", "fixture-private-note", owner.getId());
        var first = mockMvc.perform(get(monthly).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE")).andExpect(jsonPath("$.items[0].numbers").isArray())
                .andReturn();
        String deliveredJson = first.getResponse().getContentAsString();
        var delivered = jsonMapper.readTree(deliveredJson);
        assertThat(delivered.propertyNames()).containsExactlyInAnyOrder("month", "id", "source", "verified", "items", "rating");
        assertThat(delivered.path("month").asString()).isEqualTo("2026-11");
        assertThat(delivered.path("rating").isNull()).isTrue();
        assertThat(delivered.path("verified").asBoolean()).isFalse();
        var items = delivered.path("items");
        // D58: uncategorised spending gives the pattern and salary-plan sections plus the T25 deficit action.
        assertThat(items.size()).isEqualTo(3);
        assertThat(items.get(0).path("actionId").asString()).isEqualTo("SPENDING_PATTERN");
        assertThat(items.get(1).path("actionId").asString()).isEqualTo("SALARY_PLAN");
        var item = items.get(2);
        assertThat(item.propertyNames()).containsExactlyInAnyOrder("actionId", "text", "numbers");
        assertThat(item.path("actionId").asString()).isEqualTo("REVIEW_SPENDING_DEFICIT");
        assertThat(item.path("text").asString()).isEqualTo(
                "Recorded spending exceeds recorded income by LKR 50.23. Review the shortfall while protecting essential costs.");
        assertThat(item.path("numbers").size()).isEqualTo(1);
        var number = item.path("numbers").get(0);
        assertThat(number.propertyNames()).containsExactlyInAnyOrder("factKey", "value", "unit", "period");
        assertThat(number.path("factKey").asString()).isEqualTo("SPENDING_DEFICIT");
        assertThat(number.path("value").isNumber()).isTrue();
        assertThat(number.path("value").decimalValue()).isEqualByComparingTo("50.23");
        assertThat(number.path("unit").asString()).isEqualTo("LKR");
        assertThat(number.path("period").asString()).isEqualTo("2026-11");
        assertThat(deliveredJson).doesNotContain("fixture-private-merchant", "fixture-private-note", "Advice wallet",
                owner.getEmail(), owner.getFullName(), auth);
        long id = delivered.path("id").longValue();
        assertThat(id).isPositive();
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
        var empty = mockMvc.perform(get(monthly).header("Authorization", foreign)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andReturn();
        var emptyAdvice = jsonMapper.readTree(empty.getResponse().getContentAsString());
        assertThat(emptyAdvice.propertyNames()).containsExactlyInAnyOrder("month", "id", "source", "verified", "items", "rating");
        assertThat(emptyAdvice.path("source").asString()).isEqualTo("RULE");
        assertThat(emptyAdvice.path("id").isNull()).isTrue();
        assertThat(emptyAdvice.path("rating").isNull()).isTrue();
    }
}
