package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.document.application.dto.DocumentDto;
import in.anytimework.atwbackend.document.application.dto.UploadDocumentCommand;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarOtpChallengeDto;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarVerificationResultDto;
import in.anytimework.atwbackend.kyc.application.dto.MobileOtpChallengeDto;
import in.anytimework.atwbackend.kyc.domain.AadhaarVerificationStatus;
import in.anytimework.atwbackend.kyc.domain.WorkerAadhaarVerification;
import in.anytimework.atwbackend.kyc.domain.WorkerAadhaarVerificationRepository;
import in.anytimework.atwbackend.kyc.domain.WorkerKycProfileRepository;
import in.anytimework.atwbackend.kyc.domain.WorkerMobileOtp;
import in.anytimework.atwbackend.kyc.domain.WorkerMobileOtpRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.RateLimiter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * The "add worker via Aadhaar" intake wizard: proves control of a mobile number by SMS OTP, then
 * runs a live Aadhaar e-KYC OTP verification (see {@link AadhaarKycPort}) to pull UIDAI-sourced
 * identity details straight into the create-worker form - replacing manual data entry, and, since a
 * successful live verification is itself the proof of identity, the manual document-review workflow
 * this module started with.
 *
 * <p>The mobile-OTP and Aadhaar-OTP steps are deliberately independent, not cryptographically
 * chained together: this is an admin-operated back-office wizard, not public self-service, so the UI
 * simply sequencing the two steps is sufficient today. Revisit if/when this flow is ever exposed
 * directly to workers in the future worker portal.</p>
 *
 * <p>A concrete class, not an interface: only {@code kyc.api.admin.AdminWorkerIntakeController}
 * (same module) calls it - the same shape as {@code worker.application.WorkerAddressService}.</p>
 */
@Service
public class WorkerIntakeService {

    private static final Duration MOBILE_OTP_TTL = Duration.ofMinutes(10);
    private static final Duration AADHAAR_OTP_TTL = Duration.ofMinutes(10);
    private static final int MAX_MOBILE_OTP_ATTEMPTS = 5;
    private static final int MAX_AADHAAR_OTP_ATTEMPTS = 3;
    private static final int MAX_CONCURRENT_VERIFY_RETRIES = 5;
    private static final Duration SEND_RATE_LIMIT_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SENDS_PER_SUBJECT = 3;
    private static final int MAX_SENDS_PER_IP = 10;
    private static final int MINIMUM_WORKER_AGE_YEARS = 18;

    private final WorkerMobileOtpRepository mobileOtps;
    private final WorkerAadhaarVerificationRepository verifications;
    private final WorkerKycProfileRepository kycProfiles;
    private final IdentityNumberHasher aadhaarHasher;
    private final AadhaarKycPort aadhaarKycPort;
    private final NotificationService notificationService;
    private final GeographyLookupService geographyLookup;
    private final DocumentService documentService;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WorkerIntakeService(WorkerMobileOtpRepository mobileOtps, WorkerAadhaarVerificationRepository verifications,
                               WorkerKycProfileRepository kycProfiles, IdentityNumberHasher aadhaarHasher,
                               AadhaarKycPort aadhaarKycPort, NotificationService notificationService,
                               GeographyLookupService geographyLookup, DocumentService documentService,
                               RateLimiter rateLimiter, PasswordEncoder passwordEncoder, Clock clock) {
        this.mobileOtps = mobileOtps;
        this.verifications = verifications;
        this.kycProfiles = kycProfiles;
        this.aadhaarHasher = aadhaarHasher;
        this.aadhaarKycPort = aadhaarKycPort;
        this.notificationService = notificationService;
        this.geographyLookup = geographyLookup;
        this.documentService = documentService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public MobileOtpChallengeDto sendMobileOtp(String mobileNumber, String clientIp) {
        requireWithinRateLimit("mobile-send:number:" + mobileNumber, MAX_SENDS_PER_SUBJECT);
        requireWithinRateLimit("mobile-send:ip:" + clientIp, MAX_SENDS_PER_IP);

        String code = generateOtpCode();
        WorkerMobileOtp otp = new WorkerMobileOtp(mobileNumber, passwordEncoder.encode(code),
                clock.instant().plus(MOBILE_OTP_TTL), 0);
        otp = mobileOtps.save(otp);
        notificationService.sendSms(mobileNumber, "Your Anytime Work verification code is " + code
                + ". It expires in " + MOBILE_OTP_TTL.toMinutes() + " minutes.");
        return new MobileOtpChallengeDto(otp.getId(), (int) MOBILE_OTP_TTL.toSeconds());
    }

    /** Retries on a losing optimistic-lock race - same reasoning as {@code OtpService.verifyAndConsume}. */
    public void verifyMobileOtp(UUID otpId, String code) {
        for (int attempt = 0; attempt < MAX_CONCURRENT_VERIFY_RETRIES; attempt++) {
            try {
                attemptVerifyMobileOtp(otpId, code);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                // Another concurrent verify of this same challenge committed first; retry against
                // the now-current row rather than dropping this guess's attempt on the floor.
            }
        }
        throw new ValidationException("Invalid or expired verification code");
    }

    /**
     * Deliberately NOT {@code @Transactional}: on a wrong code this must persist the incremented
     * attempt count even though the method then throws - wrapping it in a transaction would roll
     * that save back along with the exception under Spring's default rollback-on-unchecked-exception
     * rule (see {@code OtpService.attemptVerify}'s Javadoc for the full reasoning; same pattern here).
     */
    private void attemptVerifyMobileOtp(UUID otpId, String code) {
        WorkerMobileOtp otp = mobileOtps.findById(otpId)
                .orElseThrow(() -> new ValidationException("Invalid or expired verification code"));
        if (otp.isConsumed() || otp.isExpired(clock.instant())) {
            throw new ValidationException("Invalid or expired verification code");
        }
        if (otp.getAttempts() >= MAX_MOBILE_OTP_ATTEMPTS) {
            throw new BusinessRuleException("Too many invalid attempts. Please request a new code.");
        }
        otp.incrementAttempts();
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            mobileOtps.save(otp);
            throw new ValidationException("Invalid or expired verification code");
        }
        otp.consume(clock.instant());
        mobileOtps.save(otp);
    }

    @Transactional
    public AadhaarOtpChallengeDto sendAadhaarOtp(String aadhaarNumber, boolean consentGiven, String clientIp) {
        if (!consentGiven) {
            throw new ValidationException("Worker consent is required before verifying Aadhaar details.");
        }
        String hash = aadhaarHasher.hash(aadhaarNumber);
        if (kycProfiles.existsByAadhaarNumberHash(hash)) {
            throw new ConflictException("This Aadhaar number is already registered to another worker.");
        }
        requireWithinRateLimit("aadhaar-send:hash:" + hash, MAX_SENDS_PER_SUBJECT);
        requireWithinRateLimit("aadhaar-send:ip:" + clientIp, MAX_SENDS_PER_IP);

        String referenceId = aadhaarKycPort.sendOtp(aadhaarNumber);
        Instant now = clock.instant();
        WorkerAadhaarVerification verification = new WorkerAadhaarVerification(
                aadhaarNumber, hash, referenceId, now.plus(AADHAAR_OTP_TTL), now, clientIp);
        verification = verifications.save(verification);
        return new AadhaarOtpChallengeDto(verification.getId(), (int) AADHAAR_OTP_TTL.toSeconds());
    }

    /**
     * Deliberately NOT retried on an optimistic-lock conflict (unlike {@link #verifyMobileOtp}) and
     * deliberately NOT wrapped in a single {@code @Transactional} span (same reason as
     * {@link #attemptVerifyMobileOtp}: a failed attempt must still persist its incremented count).
     * {@link AadhaarKycPort#verifyOtp} is an external, typically single-use, paid provider call -
     * blindly retrying the whole method on a losing race would re-invoke it needlessly and would
     * fail anyway, since the provider OTP is already spent. A genuine conflict here is rare enough
     * (two concurrent submits of the very same verification+code) that surfacing it as the standard
     * 409 (see {@code GlobalExceptionHandler.handleOptimisticLockingFailure}) and letting the admin
     * retry is the right tradeoff.
     */
    public AadhaarVerificationResultDto verifyAadhaarOtp(UUID verificationId, String otp) {
        WorkerAadhaarVerification verification = verifications.findById(verificationId)
                .orElseThrow(() -> new ValidationException("Invalid or expired verification request"));
        if (verification.isConsumed() || verification.getStatus() == AadhaarVerificationStatus.VERIFIED) {
            throw new ValidationException("This verification has already been completed.");
        }
        if (verification.isExpired(clock.instant())) {
            throw new ValidationException("Invalid or expired verification code");
        }
        if (verification.getAttempts() >= MAX_AADHAAR_OTP_ATTEMPTS) {
            throw new BusinessRuleException("Too many invalid attempts. Please start over.");
        }
        verification.incrementAttempts();

        AadhaarVerifiedDetails details;
        try {
            details = aadhaarKycPort.verifyOtp(verification.getProviderReferenceId(), otp);
        } catch (BusinessRuleException e) {
            verifications.save(verification);
            throw e;
        }

        requireAdult(details.dateOfBirth(), verification);

        UUID photoDocumentId = storePhotoIfPresent(details.photo());
        UUID stateId = resolveStateId(details.stateName());
        UUID cityId = resolveCityId(stateId, details.districtName());

        verification.markVerified(clock.instant(), details.fullName(), details.gender(), details.dateOfBirth(),
                details.addressLine1(), details.addressLine2(), details.pincode(),
                details.stateName(), details.districtName(), photoDocumentId);
        verifications.save(verification);

        return new AadhaarVerificationResultDto(verification.getId(), details.fullName(), details.gender(),
                details.dateOfBirth(), details.addressLine1(), details.addressLine2(), details.pincode(),
                details.stateName(), stateId, details.districtName(), cityId, photoDocumentId != null);
    }

    private void requireAdult(LocalDate dateOfBirth, WorkerAadhaarVerification verification) {
        boolean underage = dateOfBirth == null
                || Period.between(dateOfBirth, LocalDate.now(clock)).getYears() < MINIMUM_WORKER_AGE_YEARS;
        if (underage) {
            verification.markFailed();
            verifications.save(verification);
            throw new BusinessRuleException("Worker must be at least " + MINIMUM_WORKER_AGE_YEARS + " years old.");
        }
    }

    private UUID storePhotoIfPresent(byte[] photo) {
        if (photo == null || photo.length == 0) {
            return null;
        }
        // UIDAI e-KYC responses return the photo as a JPEG - fixed by the provider's own format, not guessed.
        DocumentDto stored = documentService.upload(new UploadDocumentCommand(
                "WORKER_AADHAAR_PHOTO", null, "aadhaar-photo.jpg", "image/jpeg", photo));
        return stored.id();
    }

    /** Case-insensitive exact match against masterdata's state list; null (never an error) on no match - an admin can always pick manually. */
    private UUID resolveStateId(String stateName) {
        if (!StringUtils.hasText(stateName)) {
            return null;
        }
        return geographyLookup.listStates().stream()
                .filter(s -> s.name().equalsIgnoreCase(stateName.trim()))
                .map(StateSummary::id)
                .findFirst()
                .orElse(null);
    }

    private UUID resolveCityId(UUID stateId, String districtName) {
        if (stateId == null || !StringUtils.hasText(districtName)) {
            return null;
        }
        return geographyLookup.listCities(stateId).stream()
                .filter(c -> c.name().equalsIgnoreCase(districtName.trim()))
                .map(CitySummary::id)
                .findFirst()
                .orElse(null);
    }

    private void requireWithinRateLimit(String key, int maxAttempts) {
        if (!rateLimiter.allow("worker-intake:" + key, maxAttempts, SEND_RATE_LIMIT_WINDOW)) {
            throw new BusinessRuleException("Too many attempts. Please try again later.");
        }
    }

    private String generateOtpCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
