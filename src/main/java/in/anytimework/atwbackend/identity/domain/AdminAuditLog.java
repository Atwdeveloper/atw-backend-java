package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/**
 * Append-only record of a security-significant admin action (login, logout, password change, etc.).
 * {@code created_at} (from BaseEntity) is the event time; {@code correlationId} ties it to request logs.
 */
@Getter
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog extends BaseEntity {

    @Column(name = "actor_admin_id")
    private UUID actorAdminId;

    // Snapshot at write time, not a live join - admin_audit_log.actor_admin_id is ON DELETE SET
    // NULL, so once an admin account is deleted this is the only thing that still identifies who
    // performed their past actions. See the V14 migration comment.
    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "subject_type", length = 60)
    private String subjectType;

    @Column(name = "subject_id", length = 100)
    private String subjectId;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected AdminAuditLog() {
        // required by JPA
    }

    public AdminAuditLog(UUID actorAdminId, String actorEmail, String actorType, String action, String subjectType,
                         String subjectId, String detail, String ipAddress, String userAgent, String correlationId) {
        this.actorAdminId = actorAdminId;
        this.actorEmail = actorEmail;
        this.actorType = actorType;
        this.action = action;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
    }
}
