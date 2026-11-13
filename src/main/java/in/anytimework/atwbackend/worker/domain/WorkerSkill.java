package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * One skill a worker has declared, under one of their chosen designations - {@code WorkerService}
 * validates {@code skillId} resolves to a skill whose own {@code designationId} is one of the
 * worker's current {@link WorkerDesignation} rows. Plain UUID, not a JPA relationship - same
 * reasoning as {@link WorkerDesignation}.
 */
@Getter
@Entity
@Table(name = "worker_skill")
public class WorkerSkill extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    protected WorkerSkill() {
        // required by JPA
    }

    public WorkerSkill(UUID workerId, UUID skillId) {
        this.workerId = workerId;
        this.skillId = skillId;
    }
}
