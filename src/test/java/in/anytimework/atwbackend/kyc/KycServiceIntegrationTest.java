package in.anytimework.atwbackend.kyc;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.kyc.application.KycService;
import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.kyc.application.WorkerIntakeService;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarOtpChallengeDto;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarVerificationResultDto;
import in.anytimework.atwbackend.kyc.application.dto.KycProfileDto;
import in.anytimework.atwbackend.kyc.application.dto.MobileOtpChallengeDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.notification.CapturingSmsSender;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KYC coverage against the real local test Postgres: the manual "declare a number" path
 * (submitAadhaar/submitPan) plus the live Aadhaar e-KYC path ({@link WorkerIntakeService}, backed by
 * {@code StubAadhaarKycAdapter}'s fixed dev OTP "123456"), which is what {@code WorkerService.create()}
 * consumes when a request carries an {@code aadhaarVerificationId}. Every worker_kyc_profile row needs
 * a real worker.id (real FK - see WorkerKycProfile's Javadoc), so these tests create real workers via
 * WorkerService first.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({FileStorageTestConfig.class, KycServiceIntegrationTest.SmsTestConfig.class})
class KycServiceIntegrationTest {

    private static final String FIXED_DEV_OTP = "123456";
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private KycService kycService;
    @Autowired
    private WorkerIntakeService intakeService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private CapturingSmsSender smsSender;

    private record TestMasterData(UUID departmentId, UUID designationId) {
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private static String uniqueAadhaar() {
        // 12 digits, unique per call - not a real checksum (none is enforced, by design - see SubmitAadhaarRequest).
        return String.valueOf(100000000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 899999999999L));
    }

    private static String uniquePan() {
        // Valid PAN shape (5 letters, 4 digits, 1 letter), unique per call via the digit segment -
        // avoids colliding with the fixed "ABCDE1234F" literal used elsewhere in this class/the suite.
        int fourDigits = 1000 + Math.abs(UUID.randomUUID().hashCode() % 9000);
        return "ABCPE" + fourDigits + "F";
    }

    /**
     * A fresh, unique string per call rather than a fixed "127.0.0.1": {@link WorkerIntakeService}'s
     * per-IP send rate limit is backed by real Redis, so a constant value here would make this whole
     * class's Aadhaar/mobile-OTP-send tests (and any other suite run within the same 10-minute window)
     * trip each other's "too many attempts" guard. Not meant to look like a real IP - only to be a
     * distinct rate-limit bucket key - but still has to fit consent_ip's VARCHAR(45) (sized for a real
     * IPv6 address), hence a bare UUID rather than a longer descriptive string.
     */
    private static String uniqueClientIp() {
        return UUID.randomUUID().toString();
    }

    private static String extractOtp(String text) {
        Matcher m = SIX_DIGITS.matcher(text);
        assertThat(m.find()).as("6-digit code present in: " + text).isTrue();
        return m.group(1);
    }

    private TestMasterData setupMasterData() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        DesignationResponse designation = designationService.create(
                new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), null));
        return new TestMasterData(department.id(), designation.id());
    }

    private UUID createWorker() {
        TestMasterData md = setupMasterData();
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(),
                Gender.MALE, null, uniqueMobile(), null, md.departmentId(), null,
                Set.of(md.designationId()), md.designationId(), Set.of()));
        return worker.id();
    }

    private WorkerResponse createWorkerWithAadhaarVerification(UUID verificationId) {
        TestMasterData md = setupMasterData();
        return workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(),
                Gender.MALE, null, uniqueMobile(), null, md.departmentId(), null,
                Set.of(md.designationId()), md.designationId(), Set.of(), verificationId));
    }

    /** Drives the full Aadhaar e-KYC wizard (send OTP, verify with the stub's fixed dev OTP). */
    private UUID verifyAadhaarViaStub(String aadhaarNumber) {
        AadhaarOtpChallengeDto challenge = intakeService.sendAadhaarOtp(aadhaarNumber, true, uniqueClientIp());
        AadhaarVerificationResultDto result = intakeService.verifyAadhaarOtp(challenge.verificationId(), FIXED_DEV_OTP);
        return result.verificationId();
    }

    @Test
    void submitAadhaar_movesProfileToPendingAndMasksByDefault() {
        UUID workerId = createWorker();
        String aadhaar = uniqueAadhaar();

        KycProfileDto profile = kycService.submitAadhaar(workerId, aadhaar);

        assertThat(profile.overallStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(profile.aadhaarNumber()).isNotEqualTo(aadhaar);
        assertThat(profile.aadhaarNumber()).endsWith(aadhaar.substring(aadhaar.length() - 4));
    }

    @Test
    void submitAadhaar_duplicateAcrossDifferentWorkers_isRejected() {
        UUID workerA = createWorker();
        UUID workerB = createWorker();
        String aadhaar = uniqueAadhaar();

        kycService.submitAadhaar(workerA, aadhaar);

        assertThatThrownBy(() -> kycService.submitAadhaar(workerB, aadhaar)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submitAadhaar_resubmittingTheSameNumberForTheSameWorker_isAllowed() {
        UUID workerId = createWorker();
        String aadhaar = uniqueAadhaar();
        kycService.submitAadhaar(workerId, aadhaar);

        KycProfileDto resubmitted = kycService.submitAadhaar(workerId, aadhaar);

        assertThat(resubmitted.overallStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void submitPan_duplicateAcrossDifferentWorkers_isRejected() {
        UUID workerA = createWorker();
        UUID workerB = createWorker();
        String pan = uniquePan();

        kycService.submitPan(workerA, pan);

        assertThatThrownBy(() -> kycService.submitPan(workerB, pan)).isInstanceOf(ConflictException.class);
    }

    @Test
    void submitPan_resubmittingTheSameNumberForTheSameWorker_isAllowed() {
        UUID workerId = createWorker();
        String pan = uniquePan();
        kycService.submitPan(workerId, pan);

        KycProfileDto resubmitted = kycService.submitPan(workerId, pan);

        assertThat(resubmitted.overallStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void getProfile_forAWorkerWithNoSubmissionAtAll_returnsNotStartedPlaceholder() {
        UUID workerId = createWorker();
        KycProfileDto profile = kycService.getProfile(workerId);
        assertThat(profile.overallStatus()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(profile.verificationRef()).isNull();
    }

    @Test
    void createWorkerWithAadhaarVerification_marksKycVerifiedWithVerificationRef() {
        UUID verificationId = verifyAadhaarViaStub(uniqueAadhaar());

        WorkerResponse worker = createWorkerWithAadhaarVerification(verificationId);

        KycProfileDto profile = kycService.getProfile(worker.id());
        assertThat(profile.overallStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(profile.verifiedAt()).isNotNull();
        assertThat(profile.verificationRef()).isNotBlank();
    }

    @Test
    void aadhaarVerification_cannotBeReusedForASecondWorker() {
        UUID verificationId = verifyAadhaarViaStub(uniqueAadhaar());
        createWorkerWithAadhaarVerification(verificationId);

        assertThatThrownBy(() -> createWorkerWithAadhaarVerification(verificationId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sendAadhaarOtp_forANumberAlreadyRegisteredToAWorker_isRejectedBeforeCallingTheProvider() {
        UUID workerId = createWorker();
        String aadhaar = uniqueAadhaar();
        kycService.submitAadhaar(workerId, aadhaar);

        assertThatThrownBy(() -> intakeService.sendAadhaarOtp(aadhaar, true, uniqueClientIp()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sendAadhaarOtp_withoutConsent_isRejected() {
        assertThatThrownBy(() -> intakeService.sendAadhaarOtp(uniqueAadhaar(), false, uniqueClientIp()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void verifyAadhaarOtp_withWrongCode_isRejected() {
        AadhaarOtpChallengeDto challenge = intakeService.sendAadhaarOtp(uniqueAadhaar(), true, uniqueClientIp());

        assertThatThrownBy(() -> intakeService.verifyAadhaarOtp(challenge.verificationId(), "000000"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void completeFromVerification_beforeTheOtpIsVerified_isRejected() {
        UUID workerId = createWorker();
        AadhaarOtpChallengeDto challenge = intakeService.sendAadhaarOtp(uniqueAadhaar(), true, uniqueClientIp());

        assertThatThrownBy(() -> kycService.completeFromVerification(workerId, challenge.verificationId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void completeFromVerification_withUnknownVerificationId_isRejected() {
        UUID workerId = createWorker();

        assertThatThrownBy(() -> kycService.completeFromVerification(workerId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completeAadhaarVerification_onAnExistingManuallyCreatedWorker_upgradesKycToVerified() {
        UUID workerId = createWorker();
        UUID verificationId = verifyAadhaarViaStub(uniqueAadhaar());

        workerService.completeAadhaarVerification(workerId, verificationId);

        assertThat(kycService.getStatus(workerId)).isEqualTo(KycStatus.VERIFIED);
    }

    @Test
    void mobileOtp_sendThenVerifyWithTheCapturedCode_succeeds() {
        MobileOtpChallengeDto challenge = intakeService.sendMobileOtp(uniqueMobile(), uniqueClientIp());
        String code = extractOtp(smsSender.last().body());

        assertThatCode(() -> intakeService.verifyMobileOtp(challenge.otpId(), code)).doesNotThrowAnyException();
    }

    @Test
    void mobileOtp_verifyWithWrongCode_isRejected() {
        MobileOtpChallengeDto challenge = intakeService.sendMobileOtp(uniqueMobile(), uniqueClientIp());

        assertThatThrownBy(() -> intakeService.verifyMobileOtp(challenge.otpId(), "000000"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void findWorkerIdsByStatus_andBatchGetStatuses_agreeWithGetStatus() {
        UUID verifiedVerificationId = verifyAadhaarViaStub(uniqueAadhaar());
        WorkerResponse verifiedWorker = createWorkerWithAadhaarVerification(verifiedVerificationId);
        UUID pendingWorker = createWorker();
        kycService.submitPan(pendingWorker, uniquePan());

        assertThat(kycService.findWorkerIdsByStatus(Set.of(KycStatus.VERIFIED))).contains(verifiedWorker.id());
        assertThat(kycService.findWorkerIdsByStatus(Set.of(KycStatus.VERIFIED))).doesNotContain(pendingWorker);

        Map<UUID, KycStatus> statuses = kycService.getStatuses(Set.of(verifiedWorker.id(), pendingWorker));
        assertThat(statuses.get(verifiedWorker.id())).isEqualTo(KycStatus.VERIFIED);
        assertThat(statuses.get(pendingWorker)).isEqualTo(KycStatus.PENDING);
    }

    @TestConfiguration
    static class SmsTestConfig {
        @Bean
        @Primary
        CapturingSmsSender capturingSmsSender() {
            return new CapturingSmsSender();
        }
    }
}
