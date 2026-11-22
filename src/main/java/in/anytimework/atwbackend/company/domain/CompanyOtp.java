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
 * A one-time passcode (mandatory 2FA on every company-user login - the developer explicitly chose to
 * mirror admin's mandatory OTP rather than skip it for the company side). The code itself is stored
 * only as a hash; verification is attempt-limited and time-bound. Mirrors
 * {@code identity.domain.AdminOtp} field-for-field.
 *
 * <p>{@link CompanyOtpChannel}/{@link CompanyOtpPurpose} are this module's own copies of
 * {@code identity.domain.OtpChannel}/{@code OtpPurpose} rather than a cross-module reach into
 * identity's internal domain package - the whole company-auth chain is a deliberate, independent
 * parallel implementation (see this codebase's locked "group by business domain, never by user
 * type" rule), not an identity-module extension.</p>
 */
@Getter
@Entity
@Table(name = "company_otp")
public class CompanyOtp extends BaseEntity {

    @Column(name = "company_user_id", nullable = false)
    private UUID companyUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyOtpChannel channel;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompanyOtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    protected CompanyOtp() {
        // required by JPA
    }

    public CompanyOtp(UUID companyUserId, CompanyOtpChannel channel, String codeHash, CompanyOtpPurpose purpose,
                      Instant expiresAt, int resendCount) {
        this.companyUserId = companyUserId;
        this.channel = channel;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.resendCount = resendCount;
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
