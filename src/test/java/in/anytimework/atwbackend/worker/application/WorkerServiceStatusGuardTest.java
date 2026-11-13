package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.kyc.application.KycService;
import in.anytimework.atwbackend.masterdata.application.lookup.DepartmentLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillLookupService;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerDesignationRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import in.anytimework.atwbackend.worker.domain.WorkerSkillRepository;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for the status-transition guards in WorkerService -
 * mirrors MinimumWageRateServiceTest's style for a service whose business rules don't need a real
 * database to verify. Covers every rule with no DB-level backstop: a reason is required for
 * SUSPENDED/BLACKLISTED/INACTIVE; entering OR leaving BLACKLISTED requires a super-admin; REGISTERED/
 * KYC_PENDING are never a valid admin-chosen target; and ACTIVE is unreachable directly from
 * REGISTERED/KYC_PENDING (a worker must complete KYC first).
 */
class WorkerServiceStatusGuardTest {

    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final WorkerDesignationRepository workerDesignations = mock(WorkerDesignationRepository.class);
    private final WorkerSkillRepository workerSkills = mock(WorkerSkillRepository.class);
    private final WorkerStatusHistoryRepository statusHistory = mock(WorkerStatusHistoryRepository.class);
    private final DepartmentLookupService departmentLookup = mock(DepartmentLookupService.class);
    private final IndustryLookupService industryLookup = mock(IndustryLookupService.class);
    private final DesignationLookupService designationLookup = mock(DesignationLookupService.class);
    private final SkillLookupService skillLookup = mock(SkillLookupService.class);
    private final KycService kycService = mock(KycService.class);
    private final FaceAuthService faceAuthService = mock(FaceAuthService.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final WorkerProfilePhotoSupport photoSupport = mock(WorkerProfilePhotoSupport.class);
    private final CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T06:00:00Z"), ZoneOffset.UTC);

    private final Worker worker = new Worker("WRK0000001", "Ramesh", null, "Kumar", Gender.MALE,
            null, "9876543210", null, UUID.randomUUID(), null);

    private WorkerService service() {
        return new WorkerService(workers, workerDesignations, workerSkills, statusHistory, departmentLookup,
                industryLookup, designationLookup, skillLookup, kycService, faceAuthService, documentService, photoSupport,
                currentAdmin, clock);
    }

    private void stubExistingWorker() {
        when(workers.findById(any())).thenReturn(Optional.of(worker));
        when(workers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workerDesignations.findByWorkerId(any())).thenReturn(List.of());
        when(workerSkills.findByWorkerId(any())).thenReturn(List.of());
        when(kycService.getStatus(any())).thenReturn(in.anytimework.atwbackend.kyc.application.KycStatus.NOT_STARTED);
    }

    @Test
    void updateStatus_toSuspended_withNoReason_isRejected() {
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.SUSPENDED, "  "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void updateStatus_toInactive_withNoReason_isRejected() {
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.INACTIVE, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateStatus_toActive_fromInactive_withNoReason_isAllowed() {
        worker.changeStatus(WorkerStatus.INACTIVE, "On leave");
        stubExistingWorker();
        assertThatCode(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .doesNotThrowAnyException();
    }

    @Test
    void updateStatus_toActive_directlyFromRegistered_isRejected() {
        // Worker starts REGISTERED by construction - a worker must complete KYC (KYC_PENDING ->
        // ACTIVE, via activateAfterKycVerified) rather than being force-activated by an admin here.
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must complete KYC");
    }

    @Test
    void updateStatus_toActive_directlyFromKycPending_isRejected() {
        worker.changeStatus(WorkerStatus.KYC_PENDING, null);
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must complete KYC");
    }

    @Test
    void updateStatus_toRegisteredOrKycPending_isRejected() {
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.REGISTERED, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reached automatically");
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.KYC_PENDING, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reached automatically");
    }

    @Test
    void updateStatus_toSameStatusWorkerAlreadyHas_isRejected() {
        stubExistingWorker();
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.REGISTERED, null))
                .isInstanceOf(ValidationException.class);
        // REGISTERED is also NOT_DIRECTLY_SETTABLE, so use a pair where the no-op check is what fires.
        worker.changeStatus(WorkerStatus.ACTIVE, null);
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already");
    }

    @Test
    void updateStatus_toBlacklisted_byNonSuperAdmin_isRejected() {
        stubExistingWorker();
        when(currentAdmin.isSuperAdmin()).thenReturn(false);
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.BLACKLISTED, "Fraudulent documents"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("super-admin");
    }

    @Test
    void updateStatus_toBlacklisted_bySuperAdmin_isAllowed() {
        stubExistingWorker();
        when(currentAdmin.isSuperAdmin()).thenReturn(true);
        assertThatCode(() -> service().updateStatus(worker.getId(), WorkerStatus.BLACKLISTED, "Fraudulent documents"))
                .doesNotThrowAnyException();
    }

    @Test
    void updateStatus_fromBlacklisted_byNonSuperAdmin_isRejected() {
        // A non-super-admin must not be able to silently reverse a super-admin's blacklist decision
        // just by requesting a different target status - leaving BLACKLISTED is as privileged as entering it.
        worker.changeStatus(WorkerStatus.BLACKLISTED, "Fraudulent documents");
        stubExistingWorker();
        when(currentAdmin.isSuperAdmin()).thenReturn(false);
        assertThatThrownBy(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("super-admin");
    }

    @Test
    void updateStatus_fromBlacklisted_bySuperAdmin_isAllowed() {
        worker.changeStatus(WorkerStatus.BLACKLISTED, "Fraudulent documents");
        stubExistingWorker();
        when(currentAdmin.isSuperAdmin()).thenReturn(true);
        assertThatCode(() -> service().updateStatus(worker.getId(), WorkerStatus.ACTIVE, null))
                .doesNotThrowAnyException();
    }

    @Test
    void activateAfterKycVerified_doesNotReactivateASuspendedWorker() {
        worker.changeStatus(WorkerStatus.SUSPENDED, "Under investigation");
        when(workers.findById(any())).thenReturn(Optional.of(worker));

        service().activateAfterKycVerified(worker.getId());

        assertThatCode(() -> {}).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(worker.getStatus()).isEqualTo(WorkerStatus.SUSPENDED);
    }

    @Test
    void activateAfterKycVerified_fromKycPending_activates() {
        worker.changeStatus(WorkerStatus.KYC_PENDING, null);
        when(workers.findById(any())).thenReturn(Optional.of(worker));
        when(workers.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().activateAfterKycVerified(worker.getId());

        org.assertj.core.api.Assertions.assertThat(worker.getStatus()).isEqualTo(WorkerStatus.ACTIVE);
    }
}
