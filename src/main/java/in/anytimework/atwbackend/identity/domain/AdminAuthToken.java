package in.anytimework.atwbackend.identity.domain;

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
 * A single-use link token for an admin: password reset or staff invite (see {@link AuthTokenType}).
 * Stored as a SHA-256 hash; usable once before expiry.
 */
@Getter
@Entity
@Table(name = "admin_auth_token")
public class AdminAuthToken extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthTokenType type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected AdminAuthToken() {
        // required by JPA
    }

    public AdminAuthToken(UUID adminUserId, String tokenHash, AuthTokenType type, Instant expiresAt) {
        this.adminUserId = adminUserId;
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
