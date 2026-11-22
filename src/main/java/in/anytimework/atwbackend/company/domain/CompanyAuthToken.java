package in.anytimework.atwbackend.company.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use link token for a company user: password reset or owner invite (see
 * {@link CompanyAuthTokenType}). Stored as a SHA-256 hash; usable once before expiry. Mirrors
 * {@code identity.domain.AdminAuthToken} field-for-field, including the exact
 * {@code isUsable}/{@code markUsed} shape.
 */
@Getter
@Entity
@Table(name = "company_auth_token")
public class CompanyAuthToken extends BaseEntity {

    @Column(name = "company_user_id", nullable = false)
    private UUID companyUserId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyAuthTokenType type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected CompanyAuthToken() {
        // required by JPA
    }

    public CompanyAuthToken(UUID companyUserId, String tokenHash, CompanyAuthTokenType type, Instant expiresAt) {
        this.companyUserId = companyUserId;
        this.tokenHash = tokenHash;
        this.type = type;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
