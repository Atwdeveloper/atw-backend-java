package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The statutory minimum daily wage for a (state, skill category) pair, effective from a given date.
 * Deliberately immutable once created: {@code minDailyRate}/{@code effectiveFrom}/{@code stateId}/
 * {@code skillCategoryId} have no setters, by design - a rate change is a NEW row with its own
 * {@code effectiveFrom}, never an edit of an existing one. Government minimum-wage notifications are
 * themselves always date-effective, and a flat, overwrite-in-place table (legacy's actual design)
 * loses the history needed to recompute a past payroll period correctly. The only mutation allowed
 * is {@link #activate()}/{@link #deactivate()}, for retracting a row entered in error.
 *
 * <p>{@code stateId}/{@code skillCategoryId} are plain UUIDs into this same module's other tables,
 * not JPA relationships - consistent with {@link Skill#getDesignationId()} and the rest of this
 * module's simple-parent-pointer convention.</p>
 */
@Getter
@Entity
@Table(name = "minimum_wage_rate")
public class MinimumWageRate extends BaseEntity {

    @Column(name = "state_id", nullable = false)
    private UUID stateId;

    @Column(name = "skill_category_id", nullable = false)
    private UUID skillCategoryId;

    @Column(name = "min_daily_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal minDailyRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActiveStatus status;

    protected MinimumWageRate() {
        // required by JPA
    }

    public MinimumWageRate(UUID stateId, UUID skillCategoryId, BigDecimal minDailyRate, LocalDate effectiveFrom) {
        this.stateId = stateId;
        this.skillCategoryId = skillCategoryId;
        this.minDailyRate = minDailyRate;
        this.effectiveFrom = effectiveFrom;
        this.status = ActiveStatus.ACTIVE;
    }

    public void activate() {
        this.status = ActiveStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ActiveStatus.INACTIVE;
    }
}
