package in.anytimework.atwbackend.notification.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Audit record of every email send attempt (success or failure). {@code created_at} (from
 * {@link BaseEntity}) is the attempt time. Used for support, troubleshooting, and reconciliation.
 */
@Getter
@Entity
@Table(name = "notification_log")
public class NotificationLog extends BaseEntity {

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Column(name = "subject", length = 300)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "error_detail", columnDefinition = "text")
    private String errorDetail;

    protected NotificationLog() {
        // required by JPA
    }

    private NotificationLog(String recipient, String templateCode, String subject,
                           NotificationStatus status, String providerMessageId, String errorDetail) {
        this.recipient = recipient;
        this.templateCode = templateCode;
        this.subject = subject;
        this.status = status;
        this.providerMessageId = providerMessageId;
        this.errorDetail = errorDetail;
    }

    public static NotificationLog sent(String recipient, String templateCode, String subject, String providerMessageId) {
        return new NotificationLog(recipient, templateCode, subject, NotificationStatus.SENT, providerMessageId, null);
    }

    public static NotificationLog failed(String recipient, String templateCode, String subject, String errorDetail) {
        return new NotificationLog(recipient, templateCode, subject, NotificationStatus.FAILED, null, errorDetail);
    }
}
