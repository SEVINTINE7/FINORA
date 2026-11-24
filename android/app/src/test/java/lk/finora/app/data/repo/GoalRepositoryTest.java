package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;

import org.junit.Test;

import lk.finora.app.data.api.GoalDto;
import okhttp3.HttpUrl;

public class GoalRepositoryTest {
    private static final HttpUrl GOALS = HttpUrl.get("https://example.test/api/v1/goals");

    @Test public void contributionUrlsSitUnderTheGoal() {
        assertEquals("/api/v1/goals/7", GoalRepository.goalUrl(GOALS, 7).encodedPath());
        assertEquals("/api/v1/goals/7/contributions", GoalRepository.contributionsUrl(GOALS, 7).encodedPath());
    }

    @Test public void acceptsAllStatusesAndAnOptionalDate() throws IOException {
        for (String status : new String[]{"ACTIVE", "DONE", "CANCELLED"}) {
            GoalRepository.validate(goal(status, 100, null));
        }
        GoalRepository.validate(goal("ACTIVE", 0, "2027-01-31"));
    }

    @Test public void rejectsUnknownStatusOrPercentOutsideTheBar() {
        assertThrows(IOException.class, () -> GoalRepository.validate(goal("PAUSED", 10, null)));
        assertThrows(IOException.class, () -> GoalRepository.validate(goal("ACTIVE", 101, null)));
        assertThrows(IOException.class, () -> GoalRepository.validate(null));
    }

    private static GoalDto goal(String status, int percent, String date) {
        return new GoalDto(1, "Laptop", new BigDecimal("250000.00"), date, status,
                new BigDecimal("1000.00"), new BigDecimal("249000.00"), percent);
    }
}
