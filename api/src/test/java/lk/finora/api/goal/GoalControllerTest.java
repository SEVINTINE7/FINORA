package lk.finora.api.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** T19: goals CRUD, contributions, progress (capped at 100%), automatic DONE status, ownership. */
class GoalControllerTest extends IntegrationTest {

    private static final String FUTURE = LocalDate.now().plusMonths(6).toString();
    private static final String TODAY = LocalDate.now().toString();

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
    }

    private Map<String, Object> goal(String name, String target, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("targetAmount", target);
        body.put("targetDate", date);
        return body;
    }

    private Map<String, Object> contribution(String amount, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("date", date);
        return body;
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long createGoal(User user, String name, String target) throws Exception {
        String response = send(user, post("/api/v1/goals"), goal(name, target, FUTURE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    private void contribute(User user, Long goalId, String amount) throws Exception {
        send(user, post("/api/v1/goals/" + goalId + "/contributions"), contribution(amount, TODAY))
                .andExpect(status().isCreated());
    }

    private Long firstContributionId(User user, Long goalId) throws Exception {
        String response = send(user, get("/api/v1/goals/" + goalId + "/contributions"), null)
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get(0).get("id").asLong();
    }

    // ---------- CRUD ----------

    @Test
    void createGetUpdateDelete() throws Exception {
        send(userA, post("/api/v1/goals"), goal("New laptop", "250000.00", FUTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New laptop"))
                .andExpect(jsonPath("$.targetDate").value(FUTURE))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.saved").value(0.00))
                .andExpect(jsonPath("$.remaining").value(250000.00))
                .andExpect(jsonPath("$.progressPercent").value(0));

        Long id = createGoal(userA, "Trip", "50000.00");
        // Target date is optional
        send(userA, put("/api/v1/goals/" + id), goal("Trip to Ella", "60000.00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trip to Ella"))
                .andExpect(jsonPath("$.targetAmount").value(60000.00))
                .andExpect(jsonPath("$.targetDate").doesNotExist());

        send(userA, get("/api/v1/goals"), null).andExpect(jsonPath("$.length()").value(2));

        contribute(userA, id, "1000.00");
        send(userA, delete("/api/v1/goals/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/goals/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void invalidTargetsGive400() throws Exception {
        send(userA, post("/api/v1/goals"), goal("Laptop", "0.00", FUTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, post("/api/v1/goals"), goal("Laptop", "-500.00", FUTURE)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals"), goal("Laptop", "100.001", FUTURE)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals"), goal("Laptop", null, FUTURE)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals"), goal("", "100.00", FUTURE)).andExpect(status().isBadRequest());
        // Target date today or in the past
        send(userA, post("/api/v1/goals"), goal("Laptop", "100.00", TODAY)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals"), goal("Laptop", "100.00", "2020-01-01")).andExpect(status().isBadRequest());

        // Also on edit
        Long id = createGoal(userA, "Laptop", "100.00");
        send(userA, put("/api/v1/goals/" + id), goal("Laptop", "100.00", "2020-01-01"))
                .andExpect(status().isBadRequest());
        send(userA, put("/api/v1/goals/" + id), goal("Laptop", "0.00", FUTURE)).andExpect(status().isBadRequest());
    }

    @Test
    void badContributionsGive400() throws Exception {
        Long id = createGoal(userA, "Laptop", "1000.00");
        String tomorrow = LocalDate.now().plusDays(1).toString();

        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("0.00", TODAY))
                .andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("-5.00", TODAY))
                .andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("5.00", tomorrow))
                .andExpect(status().isBadRequest());
        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("5.00", null))
                .andExpect(status().isBadRequest());
    }

    // ---------- progress ----------

    @Test
    void progressAfterAddAndDeleteOfContributions() throws Exception {
        Long id = createGoal(userA, "Laptop", "3000.00");

        contribute(userA, id, "1000.00");
        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("0.10", "2026-01-15"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saved").value(1000.10))
                .andExpect(jsonPath("$.remaining").value(1999.90))
                .andExpect(jsonPath("$.progressPercent").value(33)); // 33.33..% rounded down

        send(userA, get("/api/v1/goals/" + id + "/contributions"), null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(1000.00)) // newest date first
                .andExpect(jsonPath("$[1].date").value("2026-01-15"));

        // Delete the 1000.00 one: only 0.10 is left
        Long newest = firstContributionId(userA, id);
        send(userA, delete("/api/v1/goals/" + id + "/contributions/" + newest), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(0.10))
                .andExpect(jsonPath("$.progressPercent").value(0));

        // Deleting it again: not found
        send(userA, delete("/api/v1/goals/" + id + "/contributions/" + newest), null)
                .andExpect(status().isNotFound());

        // Editing the goal target changes the progress
        send(userA, put("/api/v1/goals/" + id), goal("Laptop", "0.20", FUTURE))
                .andExpect(jsonPath("$.progressPercent").value(50));
    }

    @Test
    void progressIsCappedAt100AndStatusBecomesDone() throws Exception {
        Long id = createGoal(userA, "Phone", "1000.00");

        contribute(userA, id, "999.99");
        send(userA, get("/api/v1/goals/" + id), null)
                .andExpect(jsonPath("$.progressPercent").value(99))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        contribute(userA, id, "500.01");
        String json = send(userA, get("/api/v1/goals/" + id), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"saved\":1500.00").contains("\"remaining\":0.00")
                .contains("\"progressPercent\":100").contains("\"status\":\"DONE\"");

        // Raising the target makes it ACTIVE again
        send(userA, put("/api/v1/goals/" + id), goal("Phone", "2000.00", FUTURE))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.progressPercent").value(75));

        // Deleting a contribution after DONE also goes back to ACTIVE
        send(userA, put("/api/v1/goals/" + id), goal("Phone", "1500.00", FUTURE))
                .andExpect(jsonPath("$.status").value("DONE"));
        send(userA, delete("/api/v1/goals/" + id + "/contributions/" + firstContributionId(userA, id)), null)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void cancelledStaysCancelledUntilUserUncancels() throws Exception {
        Long id = createGoal(userA, "Bike", "100.00");
        Map<String, Object> cancel = goal("Bike", "100.00", FUTURE);
        cancel.put("cancelled", true);

        send(userA, put("/api/v1/goals/" + id), cancel).andExpect(jsonPath("$.status").value("CANCELLED"));
        send(userA, post("/api/v1/goals/" + id + "/contributions"), contribution("100.00", TODAY))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Edit without cancelled: back to the saved-amount status
        send(userA, put("/api/v1/goals/" + id), goal("Bike", "100.00", FUTURE))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void noDoubleCounting() throws Exception {
        Long laptop = createGoal(userA, "Laptop", "1000.00");
        Long trip = createGoal(userA, "Trip", "1000.00");
        Long otherUsers = createGoal(userB, "B's goal", "1000.00");

        contribute(userA, laptop, "100.00");
        contribute(userA, laptop, "100.00"); // same amount twice = two real contributions
        contribute(userA, trip, "300.00");
        contribute(userB, otherUsers, "999.00");

        send(userA, get("/api/v1/goals"), null)
                .andExpect(jsonPath("$[0].saved").value(200.00))
                .andExpect(jsonPath("$[1].saved").value(300.00));

        // Deleting one goal does not change another one
        send(userA, delete("/api/v1/goals/" + trip), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/goals/" + laptop), null).andExpect(jsonPath("$.saved").value(200.00));
        send(userB, get("/api/v1/goals/" + otherUsers), null).andExpect(jsonPath("$.saved").value(999.00));
    }

    @Test
    void progressPercentHelper() {
        assertThat(GoalService.progressPercent(new BigDecimal("0.00"), new BigDecimal("1.00"))).isEqualTo(0);
        assertThat(GoalService.progressPercent(new BigDecimal("0.99"), new BigDecimal("1.00"))).isEqualTo(99);
        assertThat(GoalService.progressPercent(new BigDecimal("1.00"), new BigDecimal("1.00"))).isEqualTo(100);
        assertThat(GoalService.progressPercent(new BigDecimal("5.00"), new BigDecimal("1.00"))).isEqualTo(100);
    }

    // ---------- ownership ----------

    @Test
    void otherUsersGoalGives404() throws Exception {
        Long idOfA = createGoal(userA, "Laptop", "1000.00");
        contribute(userA, idOfA, "50.00");
        Long contributionOfA = firstContributionId(userA, idOfA);
        Long idOfB = createGoal(userB, "Mine", "1000.00");

        send(userB, get("/api/v1/goals/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/goals/" + idOfA), goal("Mine", "1.00", FUTURE)).andExpect(status().isNotFound());
        send(userB, delete("/api/v1/goals/" + idOfA), null).andExpect(status().isNotFound());
        send(userB, get("/api/v1/goals/" + idOfA + "/contributions"), null).andExpect(status().isNotFound());
        send(userB, post("/api/v1/goals/" + idOfA + "/contributions"), contribution("1.00", TODAY))
                .andExpect(status().isNotFound());
        send(userB, delete("/api/v1/goals/" + idOfA + "/contributions/" + contributionOfA), null)
                .andExpect(status().isNotFound());
        // A's contribution through B's own goal id: also not found
        send(userB, delete("/api/v1/goals/" + idOfB + "/contributions/" + contributionOfA), null)
                .andExpect(status().isNotFound());
        send(userB, get("/api/v1/goals"), null).andExpect(jsonPath("$.length()").value(1));

        send(userA, get("/api/v1/goals/" + idOfA), null).andExpect(jsonPath("$.saved").value(50.00));
    }
}
