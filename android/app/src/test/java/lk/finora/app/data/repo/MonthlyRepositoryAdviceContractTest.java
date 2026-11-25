package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.math.BigDecimal;
import org.junit.Test;

public class MonthlyRepositoryAdviceContractTest {
    @Test public void decodesCanonicalFallbackNumberAndNullableFields() {
        // Same provider-disabled fixture as AdviceDeliveryControllerTest, with a synthetic delivery ID.
        Gson gson = new Gson();
        var populated = gson.fromJson("""
                {"month":"2026-11","id":1,"source":"RULE","verified":false,"items":[
                  {"actionId":"REVIEW_SPENDING_DEFICIT",
                   "text":"Recorded spending exceeds recorded income by LKR 50.23. Review the shortfall while protecting essential costs.",
                   "numbers":[{"factKey":"SPENDING_DEFICIT","value":50.23,"unit":"LKR","period":"2026-11"}]}],
                 "rating":null}
                """, MonthlyRepository.Advice.class);
        assertEquals(Long.valueOf(1), populated.id());
        assertEquals("RULE", populated.source());
        assertFalse(populated.verified());
        assertNull(populated.rating());
        var item = populated.items().get(0);
        assertEquals("REVIEW_SPENDING_DEFICIT", item.actionId());
        assertTrue(item.text().contains("LKR 50.23"));
        var fact = item.numbers().get(0);
        assertEquals("SPENDING_DEFICIT", fact.factKey());
        assertEquals(new BigDecimal("50.23"), fact.value());
        assertEquals("LKR", fact.unit());
        assertEquals("2026-11", fact.period());

        var empty = gson.fromJson("""
                {"month":"2026-11","id":null,"source":"RULE","verified":false,"items":[],"rating":null}
                """, MonthlyRepository.Advice.class);
        assertNull(empty.id());
        assertNull(empty.rating());
        assertTrue(empty.items().isEmpty());
    }
}
