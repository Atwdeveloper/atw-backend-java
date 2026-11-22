package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminOtp;
import in.anytimework.atwbackend.identity.domain.AdminOtpRepository;
import in.anytimework.atwbackend.identity.domain.OtpChannel;
import in.anytimework.atwbackend.identity.domain.OtpPurpose;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Issues and verifies one-time passcodes. Codes are stored only as BCrypt hashes. */
@Service
public class OtpService {

    private final AdminOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final AdminSecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(AdminOtpRepository otps, PasswordEncoder passwordEncoder,
                      AdminSecurityProperties properties, Clock clock) {
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedOtp(UUID otpId, UUID adminUserId, String code) {
    }

    @Transactional
    public IssuedOtp issue(UUID adminUserId) {
        return issueInternal(adminUserId, 0);
    }

    /**
     * Re-issue a fresh OTP for the admin behind {@code previousOtpId}: validates the previous
     * challenge is still live, enforces the resend cooldown and the per-chain resend cap, then
     * consumes the old challenge so it can no longer itself be verified.
     */
    @Transactional
    public IssuedOtp reissue(UUID previousOtpId) {
        AdminOtp previous = otps.findById(previousOtpId)
                .orElseThrow(() -> new ValidationException("Invalid verification request"));
        if (previous.isConsumed() || previous.isExpired(clock.instant())) {
            throw new ValidationException("Invalid or expired verification code");
        }
        Instant earliestResend = previous.getCreatedAt().plus(properties.otpResendInterval());
        if (clock.instant().isBefore(earliestResend)) {
            throw new BusinessRuleException("Please wait before requesting another code.");
        }
        if (previous.getResendCount() >= properties.otpMaxResends()) {
            throw new BusinessRuleException("Too many resend attempts. Please start over by logging in again.");
        }
        previous.consume(clock.instant());
        try {
            otps.save(previous);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Lost a race with a concurrent resend/verify of the same challenge; treat it exactly
            // like an already-consumed challenge rather than leaking a raw 500.
            throw new ValidationException("Invalid or expired verification code");
        }
        return issueInternal(previous.getAdminUserId(), previous.getResendCount() + 1);
    }

    private IssuedOtp issueInternal(UUID adminUserId, int resendCount) {
        String code = generateCode();
        AdminOtp otp = new AdminOtp(adminUserId, OtpChannel.EMAIL, passwordEncoder.encode(code),
                OtpPurpose.LOGIN, clock.instant().plus(properties.otpTtl()), resendCount);
        otps.save(otp);
        return new IssuedOtp(otp.getId(), adminUserId, code);
    }

    /** Bounded retries for a losing optimistic-lock conflict in {@link #verifyAndConsume}. */
    private static final int MAX_CONCURRENT_VERIFY_RETRIES = 5;

    /**
     * Verify a code against the challenge; consumes it on success. Returns the admin id.
     *
     * <p>Retries on {@link ObjectOptimisticLockingFailureException} rather than treating a losing
     * race as "invalid code": {@link AdminOtp} inherits an optimistic-locking {@code version}
     * column, so under truly concurrent verify attempts against the same challenge, only one save
     * per round wins. Swallowing the loser's conflict without retrying would mean that guess's
     * attempt is never actually recorded — reopening the exact brute-force window this attempt
     * counter exists to close, just via concurrency instead of the original transaction-rollback
     * bug. Retrying re-reads the now-current row (reflecting the winner's increment) so every
     * concurrent guess still gets its own attempt persisted exactly once.</p>
     */
    public UUID verifyAndConsume(UUID challengeId, String code) {
        for (int attempt = 0; attempt < MAX_CONCURRENT_VERIFY_RETRIES; attempt++) {
            try {
                return attemptVerify(challengeId, code);
            } catch (ObjectOptimisticLockingFailureException e) {
                // Another concurrent verify of this same challenge committed first; retry against
                // the now-current row rather than dropping this guess's attempt on the floor.
            }
        }
        throw new ValidationException("Invalid or expired verification code");
    }

    /**
     * One read-check-write pass of {@link #verifyAndConsume}. Deliberately NOT {@code @Transactional}:
     * on a wrong code this must persist the incremented attempt count even though the method then
     * throws, and wrapping it in a transaction would roll that save back along with the exception
     * under Spring's default rollback-on-unchecked-exception rule — exactly what silently defeated
     * {@code otpMaxAttempts} before. {@link AdminOtpRepository#save} is itself transactional per
     * call (Spring Data's {@code SimpleJpaRepository} wraps every inherited method), so each
     * {@code otps.save(otp)} below commits immediately on its own — the same pattern
     * {@code AuthService.login} already relies on for {@code registerFailedLogin}.
     */
    private UUID attemptVerify(UUID challengeId, String code) {
        AdminOtp otp = otps.findById(challengeId)
                .orElseThrow(() -> new ValidationException("Invalid or expired verification code"));
        if (otp.isConsumed() || otp.isExpired(clock.instant())) {
            throw new ValidationException("Invalid or expired verification code");
        }
        if (otp.getAttempts() >= properties.otpMaxAttempts()) {
            throw new BusinessRuleException("Too many invalid attempts. Please request a new code.");
        }
        otp.incrementAttempts();
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otps.save(otp);
            throw new ValidationException("Invalid or expired verification code");
        }
        otp.consume(clock.instant());
        otps.save(otp);
        return otp.getAdminUserId();
    }

    /** Invalidates every still-active OTP challenge for an admin, e.g. after a password change. */
    @Transactional
    public void invalidateAllActiveForAdmin(UUID adminUserId) {
        otps.invalidateAllActiveForAdmin(adminUserId, clock.instant());
    }

    /**
     * Normally a fresh random code. If {@code atw.admin.security.fixed-otp} is set - only possible
     * under the "dev" profile, enforced by {@code StartupSecurityValidator} at boot - every issued
     * OTP (login and resend alike) is that fixed value instead, so pre-SMTP environments can log in
     * without reading logs or email.
     */
    private String generateCode() {
        String fixedOtp = properties.fixedOtp();
        return StringUtils.hasText(fixedOtp) ? fixedOtp : randomNumeric(properties.otpLength());
    }

    private String randomNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
