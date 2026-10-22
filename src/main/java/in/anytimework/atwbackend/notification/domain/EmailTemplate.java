package in.anytimework.atwbackend.notification.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * An email template stored in the database, so non-developers can edit wording without a redeploy.
 * {@code subject} and {@code bodyHtml} are Thymeleaf templates rendered with per-send variables;
 * {@code code} + {@code locale} identify which template to use.
 */
@Getter
@Entity
@Table(name = "email_template")
public class EmailTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "body_html", columnDefinition = "text", nullable = false)
    private String bodyHtml;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "description", length = 300)
    private String description;

    protected EmailTemplate() {
        // required by JPA
    }

    public EmailTemplate(String code, String subject, String bodyHtml, String locale,
                         boolean active, String description) {
        this.code = code;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
        this.locale = locale;
        this.active = active;
        this.description = description;
    }
}
