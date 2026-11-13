package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * One of a worker's designations/trades (a worker may hold up to 3 - see {@code WorkerService} for
 * the cap and the "exactly one primary" rule enforced there and by
 * {@code uq_worker_designation_primary}).
 *
 * <p>{@code designationId} is a plain UUID into the masterdata module, not a JPA relationship:
 * {@code designation_industry} is this codebase's only same-module {@code @ManyToMany} precedent, so
 * a worker-to-designation join - crossing into another module's entity on one side - follows the
 * established "bare UUID, validated via the owning module's lookup service" pattern instead (see how
 * {@link Worker}'s own {@code departmentId}/{@code industryId} already do this, and how
 * {@code shared.kernel.Address.stateId}/{@code cityId} do too). A real {@code @Entity} with its own
 * synthetic id (rather than modeled as a {@code @JoinTable}-only pivot, the way
 * {@code admin_user_role}/{@code designation_industry} are) because it needs a repository of its own
 * and a non-key business column ({@code isPrimary}) - every other mapped entity in this codebase
 * follows that same "synthetic UUID id, even on a join-shaped table" convention
 * ({@code AdminUserAddress}, {@code AdminUserKyc}, ...).</p>
 */
@Getter
@Entity
@Table(name = "worker_designation")
public class WorkerDesignation extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected WorkerDesignation() {
        // required by JPA
    }

    public WorkerDesignation(UUID workerId, UUID designationId, boolean primary) {
        this.workerId = workerId;
        this.designationId = designationId;
        this.primary = primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
