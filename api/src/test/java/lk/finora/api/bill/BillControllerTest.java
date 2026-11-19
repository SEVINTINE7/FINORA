package lk.finora.api.bill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** T18: bills CRUD, due dates in short months, paid/unpaid per month, totals, ownership. */
class BillControllerTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BillService billService;

    private User userA;
    private User userB;
    private Long utilities; // default EXPENSE
    private Long salary;    // default INCOME

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        utilities = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Utilities'",
                Long.class);
        salary = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Salary'",
                Long.class);
    }

    private Map<String, Object> body(String name, String amount, int dueDay, Long categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("amount", amount);
        body.put("dueDay", dueDay);
        body.put("categoryId", categoryId);
        return body;
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long createBill(User user, String name, String amount, int dueDay) throws Exception {
        String response = send(user, post("/api/v1/bills"), body(name, amount, dueDay, utilities))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    // ---------- CRUD ----------

    @Test
    void createGetUpdateDelete() throws Exception {
        Long id = createBill(userA, "Electricity", "4500.00", 10);

        send(userA, get("/api/v1/bills/" + id), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electricity"))
                .andExpect(jsonPath("$.amount").value(4500.00))
                .andExpect(jsonPath("$.dueDay").value(10))
                .andExpect(jsonPath("$.categoryId").value(utilities));

        send(userA, put("/api/v1/bills/" + id), body("Electricity (CEB)", "5200.50", 12, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electricity (CEB)"))
                .andExpect(jsonPath("$.dueDay").value(12))
                .andExpect(jsonPath("$.categoryId").doesNotExist());

        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());
        send(userA, delete("/api/v1/bills/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/bills/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void badValuesGive400() throws Exception {
        send(userA, post("/api/v1/bills"), body("Rent", "25000.00", 32, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, post("/api/v1/bills"), body("Rent", "25000.00", 0, null)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/bills"), body("Rent", "0.00", 1, null)).andExpect(status().isBadRequest());
        send(userA, post("/api/v1/bills"), body("", "100.00", 1, null)).andExpect(status().isBadRequest());
        // Income category not allowed for a bill
        send(userA, post("/api/v1/bills"), body("Rent", "100.00", 1, salary)).andExpect(status().isBadRequest());
        send(userA, get("/api/v1/bills?month=2026-1"), null).andExpect(status().isBadRequest());
    }

    // ---------- due dates ----------

    @Test
    void dueDay31MovesToLastDayOfShortMonths() throws Exception {
        createBill(userA, "Rent", "25000.00", 31);
        createBill(userA, "Gym", "3000.00", 30);

        send(userA, get("/api/v1/bills?month=2026-02"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dueDate").value("2026-02-28"))
                .andExpect(jsonPath("$[1].dueDate").value("2026-02-28"));
        send(userA, get("/api/v1/bills?month=2028-02"), null) // leap year
                .andExpect(jsonPath("$[0].dueDate").value("2028-02-29"));
        send(userA, get("/api/v1/bills?month=2026-04"), null) // 30 days
                .andExpect(jsonPath("$[1].dueDate").value("2026-04-30"));
        send(userA, get("/api/v1/bills?month=2026-12"), null) // 31 days
                .andExpect(jsonPath("$[1].dueDate").value("2026-12-31"));
    }

    @Test
    void listIsSortedByDueDay() throws Exception {
        createBill(userA, "Phone", "1500.00", 20);
        createBill(userA, "Rent", "25000.00", 1);

        send(userA, get("/api/v1/bills?month=2026-11"), null)
                .andExpect(jsonPath("$[0].name").value("Rent"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-11-01"))
                .andExpect(jsonPath("$[1].name").value("Phone"));
    }

    // ---------- paid / unpaid ----------

    @Test
    void paidIsPerMonthAndNextMonthStartsUnpaid() throws Exception {
        Long id = createBill(userA, "Rent", "25000.00", 1);

        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());

        send(userA, get("/api/v1/bills?month=2026-11"), null).andExpect(jsonPath("$[0].paid").value(true));
        // Month rollover: December has no paid marker yet
        send(userA, get("/api/v1/bills?month=2026-12"), null).andExpect(jsonPath("$[0].paid").value(false));
    }

    @Test
    void payingTwiceGives409() throws Exception {
        Long id = createBill(userA, "Rent", "25000.00", 1);

        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());
        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void markUnpaid() throws Exception {
        Long id = createBill(userA, "Rent", "25000.00", 1);
        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());

        send(userA, delete("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/bills?month=2026-11"), null).andExpect(jsonPath("$[0].paid").value(false));
        // Already unpaid: still fine, and can be paid again
        send(userA, delete("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());
        send(userA, post("/api/v1/bills/" + id + "/paid?month=2026-11"), null).andExpect(status().isNoContent());
    }

    // ---------- totals ----------

    @Test
    void summaryAndMonthlyTotal() throws Exception {
        Long rent = createBill(userA, "Rent", "25000.00", 1);
        createBill(userA, "Phone", "1499.99", 20);
        createBill(userB, "Other user's bill", "9999.00", 5);

        send(userA, post("/api/v1/bills/" + rent + "/paid?month=2026-11"), null).andExpect(status().isNoContent());

        String json = send(userA, get("/api/v1/bills/summary?month=2026-11"), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"total\":26499.99").contains("\"paid\":25000.00").contains("\"unpaid\":1499.99");

        // Next month: nothing paid yet
        json = send(userA, get("/api/v1/bills/summary?month=2026-12"), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"paid\":0.00").contains("\"unpaid\":26499.99");

        // The method other features (T21, T31) will call
        assertThat(billService.monthlyTotal(userA.getId())).isEqualByComparingTo("26499.99");
        assertThat(billService.monthlyTotal(createUser(Role.USER).getId()).toPlainString()).isEqualTo("0.00");
    }

    // ---------- ownership ----------

    @Test
    void otherUsersBillGives404() throws Exception {
        Long idOfA = createBill(userA, "Rent", "25000.00", 1);

        send(userB, get("/api/v1/bills/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/bills/" + idOfA), body("Mine", "1.00", 1, null)).andExpect(status().isNotFound());
        send(userB, delete("/api/v1/bills/" + idOfA), null).andExpect(status().isNotFound());
        send(userB, post("/api/v1/bills/" + idOfA + "/paid?month=2026-11"), null).andExpect(status().isNotFound());
        send(userB, delete("/api/v1/bills/" + idOfA + "/paid?month=2026-11"), null).andExpect(status().isNotFound());
        send(userB, get("/api/v1/bills?month=2026-11"), null).andExpect(jsonPath("$.length()").value(0));
    }
}
