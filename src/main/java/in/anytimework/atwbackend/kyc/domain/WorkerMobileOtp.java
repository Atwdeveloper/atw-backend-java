package in.anytimework.atwbackend.kyc.domain;

import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * A one-time code proving an admin/worker has access to a mobile number, as the first step of the
 * "add worker via Aadhaar" intake flow - independent of any existing worker (none exists yet at this
 * point). Same shape as identity's {@code AdminOtp} (hash-at-rest, time-bound, attempt-limited,
 * resend-capped) but deliberately a separate table/service: this is a one-off number-ownership check
 * sent by SMS, not an admin's device-bound login 2FA, and identity's OTP mechanism is tested,
 * security-critical code not worth the regression risk of generalizing for one new caller.
 */
@Getter
@Entity
@Table(name = "worker_mobile_otp")
public class WorkerMobileOtp extends BaseEntity {

    @Column(name = "mobile_number", nullable = false, length = 20)
    private String mobileNumber;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    protected WorkerMobileOtp() {
        // required by JPA
    }

    public WorkerMobileOtp(String mobileNumber, String codeHash, Instant expiresAt, int resendCount) {
        this.mobileNumber = mobileNumber;
        this.codeHash = codeHash;
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
