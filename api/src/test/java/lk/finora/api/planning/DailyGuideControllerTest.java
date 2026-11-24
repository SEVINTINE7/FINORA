package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

class DailyGuideControllerTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    private static final String PATH = "/api/v1/daily-guide";
    private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Colombo")); }
    private ResultActions send(User user, String path, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }
    private Map<String,Object> request(String confirmed) {
        return Map.of("availableFunds","1000.00", "plannedSavings","100.00",
                "additionalUnpaidBills","50.00", "confirmedTrackedUnpaidBills", confirmed,
                "nextIncomeDate", today().plusDays(1).toString());
    }
    private long bill(User user, String name, String amount) throws Exception {
        var response = send(user, "/api/v1/bills", Map.of("name",name,"amount",amount,"dueDay",today().getDayOfMonth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }
    @Test void ownerBillsOnlyPaidExcludedAndNoWrites() throws Exception {
        User owner = createUser(Role.USER), other = createUser(Role.USER);
        bill(owner,"unpaid","200.00");
        long paid = bill(owner,"paid","300.00");
        bill(other,"foreign","900.00");
        mockMvc.perform(post("/api/v1/bills/" + paid + "/paid")
                .param("month",today().toString().substring(0,7))
                .header("Authorization","Bearer " + tokenFor(owner))).andExpect(status().isNoContent());
        var before = jdbc.queryForList("SELECT * FROM bills WHERE user_id = ? ORDER BY id", owner.getId());
        var payments = jdbc.queryForList("SELECT p.* FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.user_id=? ORDER BY p.id", owner.getId());
        send(owner,PATH,request("200.00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trackedUnpaidBills").value(200.0))
                .andExpect(jsonPath("$.dailyAllowance").value(650.0))
                .andExpect(jsonPath("$.status").value("READY"));
        assertThat(jdbc.queryForList("SELECT * FROM bills WHERE user_id = ? ORDER BY id", owner.getId())).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT p.* FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.user_id=? ORDER BY p.id", owner.getId())).isEqualTo(payments);
    }
    @Test void changedBillTotalRequiresReconfirmation() throws Exception {
        User owner = createUser(Role.USER);
        bill(owner,"new unpaid","200.00");
        send(owner,PATH,request("0.00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BILLS_CHANGED"))
                .andExpect(jsonPath("$.dailyAllowance").doesNotExist());
    }
    @Test void noIncomeHistoryIsUnavailable() throws Exception {
        send(createUser(Role.USER),PATH,Map.of("availableFunds","1000.00","plannedSavings","0.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MISSING_NEXT_INCOME_DATE"));
    }
    @Test void explicitIncomeToday() throws Exception {
        send(createUser(Role.USER),PATH,Map.of("nextIncomeDate",today().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOME_TODAY"));
    }
    @Test void anonymousDenied() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void nonUserDenied() throws Exception {
        for (Role role : new Role[]{Role.ADMIN,Role.STAFF})
            send(createUser(role),PATH,Map.of()).andExpect(status().isForbidden());
    }
    @Test void invalidPrecisionRejected() throws Exception {
        send(createUser(Role.USER),PATH,Map.of("availableFunds","100.001"))
                .andExpect(status().isBadRequest());
    }
    @Test void negativeSavingsRejected() throws Exception {
        send(createUser(Role.USER),PATH,Map.of("plannedSavings","-1.00"))
                .andExpect(status().isBadRequest());
    }
    @Test void excessiveHorizonRejected() throws Exception {
        send(createUser(Role.USER),PATH,Map.of("nextIncomeDate",today().plusDays(367).toString()))
                .andExpect(status().isBadRequest());
    }
    @Test void lastIncomeFallbackIgnoresOtherOwner() throws Exception {
        User owner = createUser(Role.USER), other = createUser(Role.USER);
        for (User user : new User[]{owner,other}) {
            var account = send(user,"/api/v1/accounts",Map.of("name","test bank","type","BANK","openingBalance","0.00"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long id = jsonMapper.readTree(account).get("id").asLong();
            LocalDate date = user == owner ? today().minusDays(1) : today();
            send(user,"/api/v1/transactions",Map.of("accountId",id,"type","INCOME","amount","100.00","txnDate",date.toString()))
                    .andExpect(status().isCreated());
        }
        send(owner,PATH,Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeDateSource").value("LAST_RECORDED_INCOME_PLUS_ONE_MONTH"))
                .andExpect(jsonPath("$.lastRecordedIncomeDate").value(today().minusDays(1).toString()))
                .andExpect(jsonPath("$.nextIncomeDate").value(today().minusDays(1).plusMonths(1).toString()));
    }
}
