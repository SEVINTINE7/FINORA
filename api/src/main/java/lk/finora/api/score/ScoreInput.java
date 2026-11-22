package lk.finora.api.score;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "score_inputs")
public class ScoreInput {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, columnDefinition = "CHAR(7)")
    private String month;
    @Column(name = "emergency_reserve")
    private BigDecimal emergencyReserve;
    @Column(name = "reserve_as_of")
    private LocalDate reserveAsOf;
    @Column(name = "no_applicable_debt")
    private Boolean noApplicableDebt;
    @Column(name = "no_applicable_goals")
    private Boolean noApplicableGoals;
    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    protected ScoreInput() {}
    public ScoreInput(Long userId, String month) { this.userId = userId; this.month = month; }
    public BigDecimal getEmergencyReserve() { return emergencyReserve; }
    public LocalDate getReserveAsOf() { return reserveAsOf; }
    public Boolean getNoApplicableDebt() { return noApplicableDebt; }
    public Boolean getNoApplicableGoals() { return noApplicableGoals; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void confirm(lk.finora.api.score.dto.ScoreInputRequest r, LocalDateTime now) {
        emergencyReserve = r.emergencyReserve() == null ? null
                : r.emergencyReserve().setScale(2, java.math.RoundingMode.UNNECESSARY);
        reserveAsOf = r.reserveAsOf();
        noApplicableDebt = r.noApplicableDebt(); noApplicableGoals = r.noApplicableGoals(); confirmedAt = now;
    }
}
