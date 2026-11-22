package lk.finora.api.advice.rules;

import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.ai.FactSheetFactory;
import lk.finora.api.analytics.MetricsService;
import lk.finora.api.score.ScoreService;

/** Server-only orchestration for T27/T30. No public endpoint or network access in this task. */
@Service
public class RuleAdviceService {
    private final MetricsService metrics;
    private final ScoreService scores;
    public RuleAdviceService(MetricsService metrics, ScoreService scores) { this.metrics = metrics; this.scores = scores; }

    @Transactional(readOnly = true)
    public FactSheet facts(Long userId, String month) {
        var score = scores.forMonth(userId, month); // validates the month before parsing below
        return FactSheetFactory.from(metrics.forMonth(userId, YearMonth.parse(month)), score);
    }

    @Transactional(readOnly = true)
    public List<AdviceItem> forMonth(Long userId, String month) {
        return RuleAdviceEngine.generate(facts(userId, month));
    }
}
