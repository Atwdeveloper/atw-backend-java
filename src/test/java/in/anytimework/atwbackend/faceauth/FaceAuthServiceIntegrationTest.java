package in.anytimework.atwbackend.faceauth;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Worker face-enrollment coverage against the real local test Postgres, backed by
 * {@code StubFaceAuthAdapter} (fixed, always-acceptable fake embedding - see its own Javadoc).
 * Separately, {@code PythonFaceAuthAdapterTest} covers the real adapter's wire-format parsing in
 * isolation, since that external contract is the one genuinely unverified part of this feature.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class FaceAuthServiceIntegrationTest {

    private static final byte[] FAKE_JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5
    };

    @Autowired
    private FaceAuthService faceAuthService;
    @Autowired
    private WorkerFaceEnrollmentRepository enrollmentRepository;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private UUID createWorker() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        DesignationResponse designation = designationService.create(
                new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), null));
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(),
                Gender.MALE, null, uniqueMobile(), null, department.id(), null,
                Set.of(designation.id()), designation.id(), Set.of()));
        return worker.id();
    }

    @Test
    void getEnrollment_forAWorkerWithNoEnrollment_returnsNotEnrolledPlaceholder() {
        UUID workerId = createWorker();

        FaceEnrollmentDto dto = faceAuthService.getEnrollment(workerId);

        assertThat(dto.enrolled()).isFalse();
        assertThat(dto.enrolledAt()).isNull();
        assertThat(dto.photoUrl()).isNull();
        assertThat(faceAuthService.hasEnrollment(workerId)).isFalse();
    }

    @Test
    void enroll_withoutConsent_isRejected() {
        UUID workerId = createWorker();

        assertThatThrownBy(() -> faceAuthService.enroll(workerId, "face.jpg", "image/jpeg", FAKE_JPEG_BYTES,
                false, "127.0.0.1"))
                .isInstanceOf(ValidationException.class);
        assertThat(faceAuthService.hasEnrollment(workerId)).isFalse();
    }

    @Test
    void enroll_newWorker_persistsEnrollmentAndReturnsDto() {
        UUID workerId = createWorker();

        FaceEnrollmentDto dto = faceAuthService.enroll(workerId, "face.jpg", "image/jpeg", FAKE_JPEG_BYTES,
                true, "127.0.0.1");

        assertThat(dto.enrolled()).isTrue();
        assertThat(dto.enrolledAt()).isNotNull();
        assertThat(dto.livenessConfidence()).isNotNull();
        assertThat(dto.photoUrl()).isNotBlank();
        assertThat(faceAuthService.hasEnrollment(workerId)).isTrue();
    }

    /**
     * Reloads via the repository directly (a fresh read, since {@code spring.jpa.open-in-view=false}
     * means each call gets its own persistence context) - proves the {@code real[]}/{@code jsonb}
     * Hibernate mappings genuinely round-trip through Postgres, not just that the in-memory object
     * looks right immediately after save.
     */
    @Test
    void enrollment_embeddingAndQualitySnapshot_roundTripThroughPostgres() {
        UUID workerId = createWorker();

        faceAuthService.enroll(workerId, "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        WorkerFaceEnrollment reloaded = enrollmentRepository.findByWorkerId(workerId).orElseThrow();
        assertThat(reloaded.getEmbedding()).hasSize(128);
        assertThat(reloaded.getQualitySnapshot()).isNotNull();
        assertThat(reloaded.getQualitySnapshot().acceptable()).isTrue();
        assertThat(reloaded.getQualitySnapshot().faceWidthPx()).isEqualTo(400);
        assertThat(reloaded.getLivenessConfidence()).isEqualTo(0.95);
        assertThat(reloaded.getConsentGivenAt()).isNotNull();
        assertThat(reloaded.getConsentIp()).isEqualTo("127.0.0.1");
    }

    @Test
    void enroll_aSecondTime_overwritesThePreviousTemplateRatherThanAddingARow() {
        UUID workerId = createWorker();
        faceAuthService.enroll(workerId, "face1.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");
        FaceEnrollmentDto first = faceAuthService.getEnrollment(workerId);

        FaceEnrollmentDto second = faceAuthService.enroll(workerId, "face2.jpg", "image/jpeg", FAKE_JPEG_BYTES,
                true, "127.0.0.1");

        assertThat(second.enrolled()).isTrue();
        assertThat(enrollmentRepository.findByWorkerId(workerId)).isPresent();
        // Still exactly one row for this worker - the unique constraint on worker_id would otherwise
        // have surfaced as a DataIntegrityViolationException here.
        assertThat(first.workerId()).isEqualTo(second.workerId());
    }

    @Test
    void removeEnrollment_deletesTheEnrollment() {
        UUID workerId = createWorker();
        faceAuthService.enroll(workerId, "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        faceAuthService.removeEnrollment(workerId);

        assertThat(faceAuthService.hasEnrollment(workerId)).isFalse();
        assertThat(faceAuthService.getEnrollment(workerId).enrolled()).isFalse();
    }

    @Test
    void removeEnrollment_whenNothingEnrolled_isANoOp() {
        UUID workerId = createWorker();

        assertThatCode(() -> faceAuthService.removeEnrollment(workerId)).doesNotThrowAnyException();
    }
}
