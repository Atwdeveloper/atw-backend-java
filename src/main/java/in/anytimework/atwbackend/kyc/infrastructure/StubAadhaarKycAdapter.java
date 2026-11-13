package in.anytimework.atwbackend.kyc.infrastructure;

import in.anytimework.atwbackend.kyc.application.AadhaarKycPort;
import in.anytimework.atwbackend.kyc.application.AadhaarVerifiedDetails;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Development/demo Aadhaar e-KYC adapter: no real vendor account exists yet, so this simulates the
 * provider instead of calling one. Active by default ({@code atw.aadhaar-kyc.provider=stub}).
 * Returns fixed, deterministic verified details (not random) so demos and tests are reproducible.
 * Accepts only one fixed OTP ("123456"), the same dev-stub convention legacy used, so an admin can
 * still exercise the real failure path (wrong code) without a live provider.
 */
@Component
@ConditionalOnProperty(name = "atw.aadhaar-kyc.provider", havingValue = "stub", matchIfMissing = true)
public class StubAadhaarKycAdapter implements AadhaarKycPort {

    private static final Logger log = LoggerFactory.getLogger(StubAadhaarKycAdapter.class);
    private static final String FIXED_DEV_OTP = "123456";

    @Override
    public String sendOtp(String aadhaarNumber) {
        String referenceId = "stub-" + UUID.randomUUID();
        log.info("[STUB AADHAAR KYC] OTP 'sent' for reference {} (fixed dev OTP: {})", referenceId, FIXED_DEV_OTP);
        return referenceId;
    }

    @Override
    public AadhaarVerifiedDetails verifyOtp(String referenceId, String otp) {
        if (!FIXED_DEV_OTP.equals(otp)) {
            throw new BusinessRuleException("Invalid or expired OTP");
        }
        return new AadhaarVerifiedDetails(
                "Stub Verified Worker",
                "MALE",
                LocalDate.of(1995, 1, 1),
                "123 Stub Street",
                null,
                "110001",
                "Delhi",
                "New Delhi",
                null);
    }
}
