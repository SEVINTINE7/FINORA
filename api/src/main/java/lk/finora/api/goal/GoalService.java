package lk.finora.api.goal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.goal.dto.ContributionRequest;
import lk.finora.api.goal.dto.ContributionResponse;
import lk.finora.api.goal.dto.GoalRequest;
import lk.finora.api.goal.dto.GoalResponse;

/** Savings goals and the money put toward them (T19). Every method takes the caller's id. */
@Service
public class GoalService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;

    public GoalService(GoalRepository goalRepository, GoalContributionRepository contributionRepository) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
    }

    // ---------- goals CRUD (T19.1) ----------

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        List<GoalResponse> result = new ArrayList<>();
        for (Goal goal : goalRepository.findByUserIdOrderByIdAsc(userId)) {
            result.add(toResponse(goal));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long id) {
        return toResponse(findOwn(userId, id));
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest request) {
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        goal.setStatus(GoalStatus.ACTIVE);
        goalRepository.save(goal);
        return toResponse(goal);
    }

    /** Edit name, target or date, or cancel the goal. A new target can make it DONE or ACTIVE again. */
    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest request) {
        Goal goal = findOwn(userId, id);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        if (Boolean.TRUE.equals(request.cancelled())) {
            goal.setStatus(GoalStatus.CANCELLED);
        } else {
            // Not cancelled (or un-cancelled): status follows the saved amount
            goal.setStatus(GoalStatus.ACTIVE);
            updateStatus(goal);
        }
        goalRepository.save(goal);
        return toResponse(goal);
    }

    /** Deletes the goal. Its contributions are removed by the database (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long userId, Long id) {
        goalRepository.delete(findOwn(userId, id));
    }

    // ---------- contributions (T19.2) ----------

    @Transactional(readOnly = true)
    public List<ContributionResponse> listContributions(Long userId, Long goalId) {
        Goal goal = findOwn(userId, goalId);
        List<ContributionResponse> result = new ArrayList<>();
        for (GoalContribution c : contributionRepository.findByGoalIdOrderByContribDateDescIdDesc(goal.getId())) {
            result.add(toResponse(c));
        }
        return result;
    }

    /** Adds money to the goal and returns the goal with its new progress. */
    @Transactional
    public GoalResponse addContribution(Long userId, Long goalId, ContributionRequest request) {
        Goal goal = findOwn(userId, goalId);
        GoalContribution contribution = new GoalContribution();
        contribution.setGoalId(goal.getId());
        contribution.setAmount(request.amount());
        contribution.setContribDate(request.date());
        contributionRepository.save(contribution);
        updateStatus(goal);
        return toResponse(goal);
    }

    /** Removes one contribution and returns the goal with its new progress. */
    @Transactional
    public GoalResponse deleteContribution(Long userId, Long goalId, Long contributionId) {
        Goal goal = findOwn(userId, goalId);
        GoalContribution contribution = contributionRepository.findByIdAndGoalId(contributionId, goal.getId())
                .orElseThrow(() -> ApiException.notFound("Contribution not found"));
        contributionRepository.delete(contribution);
        updateStatus(goal);
        return toResponse(goal);
    }

    // ---------- progress ----------

    /** Total saved toward the goal so far, scale 2. */
    @Transactional(readOnly = true)
    public BigDecimal saved(Goal goal) {
        return contributionRepository.sumAmount(goal.getId()).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Whole percent of the target saved, rounded down and never above 100.
     * Example: 1,000.00 of 3,000.00 = 33; 3,500.00 of 3,000.00 = 100.
     */
    public static int progressPercent(BigDecimal saved, BigDecimal target) {
        int percent = saved.multiply(HUNDRED).divide(target, 0, RoundingMode.DOWN).intValue();
        return Math.min(percent, 100);
    }

    // DONE when the target is reached, ACTIVE otherwise. A cancelled goal stays cancelled.
    private void updateStatus(Goal goal) {
        if (goal.getStatus() == GoalStatus.CANCELLED) {
            return;
        }
        if (saved(goal).compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.DONE);
        } else {
            goal.setStatus(GoalStatus.ACTIVE);
        }
        goalRepository.save(goal);
    }

    // ---------- helpers ----------

    // Another user's goal gives the same 404 as a missing one (never 403)
    private Goal findOwn(Long userId, Long id) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Goal not found"));
    }

    private GoalResponse toResponse(Goal goal) {
        BigDecimal saved = saved(goal);
        BigDecimal remaining = goal.getTargetAmount().subtract(saved);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO.setScale(2);
        }
        return new GoalResponse(goal.getId(), goal.getName(), goal.getTargetAmount(), goal.getTargetDate(),
                goal.getStatus(), saved, remaining, progressPercent(saved, goal.getTargetAmount()));
    }

    private ContributionResponse toResponse(GoalContribution c) {
        return new ContributionResponse(c.getId(), c.getGoalId(), c.getAmount(), c.getContribDate());
    }
}
