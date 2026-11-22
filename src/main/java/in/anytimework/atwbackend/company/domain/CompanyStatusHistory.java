package in.anytimework.atwbackend.company.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * An immutable record of one {@link CompanyStatus} transition - mirrors
 * {@code worker.domain.WorkerStatusHistory} exactly, plus a snapshot of which admin made the change.
 * {@code changedByAdminId} is {@code ON DELETE SET NULL} (never lose the history fact just because
 * the admin later left); {@code changedByAdminEmail} is a snapshot at write time, not a live join -
 * same reasoning as {@code identity.domain.AdminAuditLog.actorEmail}. Never updated after creation;
 * {@code createdBy}/{@code createdAt} (inherited from {@code BaseEntity}) are "who changed it and
 * when" for anyone who only needs that, without a join.
 */
@Getter
@Entity
@Table(name = "company_status_history")
public class CompanyStatusHistory extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private CompanyStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private CompanyStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by_admin_id")
    private UUID changedByAdminId;

    @Column(name = "changed_by_admin_email", length = 320)
    private String changedByAdminEmail;

    protected CompanyStatusHistory() {
        // required by JPA
    }

    public CompanyStatusHistory(UUID companyId, CompanyStatus fromStatus, CompanyStatus toStatus, String reason,
                                UUID changedByAdminId, String changedByAdminEmail) {
        this.companyId = companyId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedByAdminId = changedByAdminId;
        this.changedByAdminEmail = changedByAdminEmail;
    }
}
