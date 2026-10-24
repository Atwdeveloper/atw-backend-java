package in.anytimework.atwbackend.identity.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived refresh token for an admin session (stored only as a SHA-256 hash). Rotated on
 * every use and revocable, so a stolen or logged-out token can be invalidated.
 */
@Getter
@Entity
@Table(name = "admin_refresh_token")
public class AdminRefreshToken extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    // Shared by every token descended from one original login (unchanged across rotations). Used
    // for reuse detection: replaying an already-rotated-away token revokes the whole family.
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected AdminRefreshToken() {
        // required by JPA
    }

    public AdminRefreshToken(UUID adminUserId, UUID familyId, String tokenHash, Instant expiresAt,
                             String ipAddress, String userAgent) {
        this.adminUserId = adminUserId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public void markReplacedBy(UUID newTokenId) {
        this.replacedById = newTokenId;
    }
}
