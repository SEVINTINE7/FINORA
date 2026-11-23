package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.Test;

import lk.finora.app.data.api.BudgetDto;
import okhttp3.HttpUrl;

public class BudgetRepositoryTest {
    @Test public void listAsksForOneMonth() {
        HttpUrl url = BudgetRepository.monthUrl(HttpUrl.get("https://example.test/api/v1/budgets"), "2026-11");
        assertEquals("2026-11", url.queryParameter("month"));
        assertEquals(1, url.querySize());
    }

    @Test public void acceptsAllThreeStatesAndNegativeRemaining() throws IOException {
        for (String state : new String[]{"OK", "NEAR", "OVER"}) {
            BudgetRepository.validate(budget(state, "-50.00"));
        }
    }

    @Test public void rejectsUnknownStateOrMissingAmounts() {
        assertThrows(IOException.class, () -> BudgetRepository.validate(budget("ALMOST", "10.00")));
        assertThrows(IOException.class, () -> BudgetRepository.validate(
                new BudgetDto(1, 2, "2026-11", new BigDecimal("100.00"), null, new BigDecimal("1.00"), "OK")));
        assertThrows(IOException.class, () -> BudgetRepository.validate(null));
    }

    private static BudgetDto budget(String state, String remaining) {
        return new BudgetDto(1, 2, "2026-11", new BigDecimal("100.00"), new BigDecimal("150.00"),
                new BigDecimal(remaining), state);
    }
}
