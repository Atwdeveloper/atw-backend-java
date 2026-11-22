package in.anytimework.atwbackend.company.infrastructure;

import in.anytimework.atwbackend.company.application.GstVerificationPort;
import in.anytimework.atwbackend.company.application.GstVerificationResult;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development/demo GST-verification adapter: no real vendor call is made. Active by default
 * ({@code atw.gst-verification.provider=stub}). Returns a FIXED, fully deterministic response -
 * never random - so matching/verification logic stays testable through the integration-test path;
 * this exact "stub must be deterministic" rule was already violated once in this codebase
 * ({@code StubFaceAuthAdapter}) and is not repeated here. Mirrors
 * {@code worker.infrastructure.StubBankVerificationAdapter}'s always-success convention.
 */
@Component
@ConditionalOnProperty(name = "atw.gst-verification.provider", havingValue = "stub", matchIfMissing = true)
public class StubGstVerificationAdapter implements GstVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubGstVerificationAdapter.class);

    @Override
    public GstVerificationResult verify(String gstNumber) {
        // Never log the raw GSTIN - a sensitive business identifier - even in this stub/demo path.
        log.info("[STUB GST VERIFICATION] Simulating a successful verification for GSTIN {}",
                PiiMasking.maskTail(gstNumber));
        return new GstVerificationResult(true, "ATW Stub Verified Enterprises Private Limited", "Maharashtra",
                "STUB-GST-VERIFICATION-REF", null);
    }
}
