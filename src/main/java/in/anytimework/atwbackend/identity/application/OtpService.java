package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminOtp;
import in.anytimework.atwbackend.identity.domain.AdminOtpRepository;
import in.anytimework.atwbackend.identity.domain.OtpChannel;
import in.anytimework.atwbackend.identity.domain.OtpPurpose;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String code = randomNumeric(properties.otpLength());
        AdminOtp otp = new AdminOtp(adminUserId, OtpChannel.EMAIL, passwordEncoder.encode(code),
                OtpPurpose.LOGIN, clock.instant().plus(properties.otpTtl()));
        otps.save(otp);
        return new IssuedOtp(otp.getId(), adminUserId, code);
    }

    /** Re-issue a fresh OTP for the admin behind {@code previousOtpId}, respecting the resend interval. */
    @Transactional
    public IssuedOtp reissue(UUID previousOtpId) {
        AdminOtp previous = otps.findById(previousOtpId)
                .orElseThrow(() -> new ValidationException("Invalid verification request"));
        Instant earliestResend = previous.getCreatedAt().plus(properties.otpResendInterval());
        if (clock.instant().isBefore(earliestResend)) {
            throw new BusinessRuleException("Please wait before requesting another code.");
        }
        return issue(previous.getAdminUserId());
    }

    /** Verify a code against the challenge; consumes it on success. Returns the admin id. */
    @Transactional
    public UUID verifyAndConsume(UUID challengeId, String code) {
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

    private String randomNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
