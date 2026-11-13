package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.kyc.application.KycService;
import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.kyc.application.WorkerIntakeService;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarOtpChallengeDto;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarVerificationResultDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatusHistoryResponse;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The actual point of building the kyc and worker modules together: a worker's KYC first becoming
 * VERIFIED auto-activates them to ACTIVE - entirely through the {@code KycVerifiedEvent}/
 * {@code @ApplicationModuleListener} path (the first genuinely cross-module domain event in this
 * codebase), not a direct method call. The listener runs asynchronously after the publishing
 * transaction commits (Spring Modulith's event publication registry), so this uses Awaitility rather
 * than asserting immediately. KYC verification itself (via a live Aadhaar e-KYC call through the
 * stub adapter's fixed dev OTP "123456") is synchronous - only the resulting activation is async.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class WorkerKycVerificationFlowIntegrationTest {

    private static final String FIXED_DEV_OTP = "123456";

    @Autowired
    private WorkerService workerService;
    @Autowired
    private KycService kycService;
    @Autowired
    private WorkerIntakeService intakeService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;

    private record TestMasterData(UUID departmentId, UUID designationId) {
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private static String uniqueAadhaar() {
        return String.valueOf(100000000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 899999999999L));
    }

    /**
     * A fresh, unique string per call rather than a fixed "127.0.0.1": {@link WorkerIntakeService}'s
     * per-IP send rate limit is backed by real Redis, so a constant value here would trip the "too
     * many attempts" guard across repeated test runs within the same 10-minute window. A bare UUID
     * (not a longer descriptive string) so it still fits consent_ip's VARCHAR(45).
     */
    private static String uniqueClientIp() {
        return UUID.randomUUID().toString();
    }

    private TestMasterData setupMasterData() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        DesignationResponse designation = designationService.create(
                new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), null));
        return new TestMasterData(department.id(), designation.id());
    }

    /** Drives the full Aadhaar e-KYC wizard (send OTP, verify with the stub's fixed dev OTP). */
    private UUID verifyAadhaarViaStub() {
        AadhaarOtpChallengeDto challenge = intakeService.sendAadhaarOtp(uniqueAadhaar(), true, uniqueClientIp());
        AadhaarVerificationResultDto result = intakeService.verifyAadhaarOtp(challenge.verificationId(), FIXED_DEV_OTP);
        return result.verificationId();
    }

    @Test
    void workerAutoActivatesOnceAadhaarEKycIsVerifiedAtCreationTime() {
        TestMasterData md = setupMasterData();
        UUID verificationId = verifyAadhaarViaStub();

        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Flow", null, "Test " + unique(),
                Gender.MALE, null, uniqueMobile(), null, md.departmentId(), null,
                Set.of(md.designationId()), md.designationId(), Set.of(), verificationId));

        assertThat(kycService.getStatus(worker.id())).isEqualTo(KycStatus.VERIFIED);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            WorkerResponse reloaded = workerService.get(worker.id());
            assertThat(reloaded.status()).isEqualTo(WorkerStatus.ACTIVE);
        });

        PageResponse<WorkerStatusHistoryResponse> history = workerService.listStatusHistory(worker.id(), PageRequest.of(0, 10));
        assertThat(history.content()).extracting(WorkerStatusHistoryResponse::toStatus).contains(WorkerStatus.ACTIVE);
        assertThat(history.content()).anySatisfy(h -> {
            if (h.toStatus() == WorkerStatus.ACTIVE) {
                assertThat(h.reason()).isEqualTo("KYC verified");
            }
        });
    }

    @Test
    void verifiedKycDoesNotReactivateAWorkerTheAdminHasSuspended() {
        TestMasterData md = setupMasterData();
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Flow", null, "Test " + unique(),
                Gender.MALE, null, uniqueMobile(), null, md.departmentId(), null,
                Set.of(md.designationId()), md.designationId(), Set.of()));

        workerService.updateStatus(worker.id(), WorkerStatus.SUSPENDED, "Pending investigation");

        UUID verificationId = verifyAadhaarViaStub();
        workerService.completeAadhaarVerification(worker.id(), verificationId);
        assertThat(kycService.getStatus(worker.id())).isEqualTo(KycStatus.VERIFIED);

        // Give the (non-)event handling a moment, then confirm the worker is still SUSPENDED, not reactivated.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            WorkerResponse reloaded = workerService.get(worker.id());
            assertThat(reloaded.status()).isEqualTo(WorkerStatus.SUSPENDED);
        });
    }
}
