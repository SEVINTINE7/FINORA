package lk.finora.api.insight;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.analytics.MetricsService;
import lk.finora.api.analytics.MonthMetrics;
import lk.finora.api.analytics.PatternRules;
import lk.finora.api.analytics.SpendingPattern;
import lk.finora.api.budget.dto.BudgetRequest;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.insight.dto.InsightResponse;
import tools.jackson.databind.json.JsonMapper;

/** Finds the month's spending patterns and saves them as insights (T22). */
@Service
public class InsightService {

    private final MetricsService metricsService;
    private final InsightRepository insightRepository;
    private final JsonMapper jsonMapper;

    public InsightService(MetricsService metricsService, InsightRepository insightRepository, JsonMapper jsonMapper) {
        this.metricsService = metricsService;
        this.insightRepository = insightRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Recalculates the month every time (D19), so new or edited records are always included:
     * the month's old insights are deleted and the new ones saved.
     */
    @Transactional
    public List<InsightResponse> forMonth(Long userId, String month) {
        if (month == null || !month.matches(BudgetRequest.MONTH_PATTERN)) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        YearMonth yearMonth = YearMonth.parse(month);
        MonthMetrics metrics = metricsService.forMonth(userId, yearMonth);

        insightRepository.deleteByUserIdAndMonth(userId, month);

        List<InsightResponse> result = new ArrayList<>();
        for (SpendingPattern pattern : PatternRules.detect(metrics)) {
            Insight insight = new Insight();
            insight.setUserId(userId);
            insight.setMonth(month);
            insight.setType(pattern.type());
            insight.setFacts(jsonMapper.writeValueAsString(pattern.facts()));
            insightRepository.save(insight);

            result.add(new InsightResponse(insight.getId(), pattern.type(), month, yearMonth.atDay(1),
                    yearMonth.atEndOfMonth(), pattern.facts()));
        }
        return result;
    }
}
