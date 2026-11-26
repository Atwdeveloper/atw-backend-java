package in.anytimework.atwbackend.worker.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * An immutable record of one status transition - fixes legacy's gap where a worker being
 * deactivated/blacklisted captured no reason and no per-change history, only a generic polymorphic
 * activity log. Never updated after creation; {@code createdBy}/{@code createdAt} (inherited from
 * {@code BaseEntity}) are "who changed it and when" as a raw JPA-auditing string - see
 * {@link WorkerNote}'s Javadoc for why that's still sufficient there. This entity additionally carries
 * a real {@code changedByAdminId} (mirroring {@code company.domain.CompanyStatusHistory}'s identical
 * field exactly) because a status change has a genuine SYSTEM case (the automatic KYC-verified
 * transition, triggered by an async event listener with no admin in context) that the response layer
 * needs to tell apart from an admin-made change, not just display a name for - {@code null} means
 * SYSTEM, a real id means ADMIN. {@code ON DELETE SET NULL} at the database level: never lose the
 * history fact just because the admin who made the change later left.
 */
@Getter
@Entity
@Table(name = "worker_status_history")
public class WorkerStatusHistory extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private WorkerStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private WorkerStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by_admin_id")
    private UUID changedByAdminId;

    protected WorkerStatusHistory() {
        // required by JPA
    }

    public WorkerStatusHistory(UUID workerId, WorkerStatus fromStatus, WorkerStatus toStatus, String reason,
                               UUID changedByAdminId) {
        this.workerId = workerId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedByAdminId = changedByAdminId;
    }
}
