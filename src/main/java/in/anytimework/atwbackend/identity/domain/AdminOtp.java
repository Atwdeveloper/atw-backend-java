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
 * A one-time passcode (2FA). The code itself is stored only as a BCrypt hash; verification is
 * attempt-limited and time-bound.
 */
@Getter
@Entity
@Table(name = "admin_otp")
public class AdminOtp extends BaseEntity {

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpChannel channel;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    protected AdminOtp() {
        // required by JPA
    }

    public AdminOtp(UUID adminUserId, OtpChannel channel, String codeHash, OtpPurpose purpose, Instant expiresAt) {
        this.adminUserId = adminUserId;
        this.channel = channel;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }
}
