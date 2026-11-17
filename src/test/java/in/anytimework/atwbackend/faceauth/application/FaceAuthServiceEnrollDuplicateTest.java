package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for {@code FaceAuthServiceImpl.enroll}'s duplicate-face
 * check - same reason {@code FaceAuthServiceIdentifyTest} exists for {@code identify}:
 * {@link in.anytimework.atwbackend.faceauth.infrastructure.StubFaceAuthAdapter} returns a genuinely
 * random embedding on every call, so a confident cross-worker match can never be forced through the
 * real stub/integration path - only a controlled, mocked {@link FaceAuthPort} can exercise this
 * deterministically. {@link in.anytimework.atwbackend.faceauth.FaceAuthServiceIntegrationTest} covers
 * the real enroll/remove/getEnrollment/excludeFromMatching wiring against Postgres.
 *
 * <p>Every embedding below is a 128-dim unit vector in the (index 0, index 1) plane, same convention
 * as {@code FaceAuthServiceIdentifyTest} - cosine similarity to the new photo's embedding is exactly
 * the dot product of their first two components, chosen to land on a specific, known score.</p>
 */
class FaceAuthServiceEnrollDuplicateTest {

    private final WorkerFaceEnrollmentRepository enrollments = mock(WorkerFaceEnrollmentRepository.class);
    private final FaceAuthPort faceAuthPort = mock(FaceAuthPort.class);
    private final FaceEnrollmentPhotoSupport photoSupport = mock(FaceEnrollmentPhotoSupport.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final FaceIdentifyProperties identifyProperties = new FaceIdentifyProperties(0.5, 0.05);
    private final FaceDuplicateProperties duplicateProperties = new FaceDuplicateProperties(0.5);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T06:00:00Z"), ZoneOffset.UTC);

    private final FaceAuthServiceImpl service = new FaceAuthServiceImpl(
            enrollments, faceAuthPort, photoSupport, documentService, identifyProperties, duplicateProperties, clock);

    private static final byte[] PHOTO_BYTES = {1, 2, 3};
    private static final FaceQualitySnapshot ACCEPTABLE_QUALITY = new FaceQualitySnapshot(400, 0.9, 0.5, true, List.of());

    /** Same convention as FaceAuthServiceIdentifyTest.vectorWithCosine. */
    private static float[] vectorWithCosine(double cos0) {
        float[] v = new float[128];
        v[0] = (float) cos0;
        v[1] = (float) Math.sqrt(Math.max(0, 1 - cos0 * cos0));
        return v;
    }

    private static WorkerFaceEnrollment enrollmentWithEmbedding(UUID workerId, float[] embedding) {
        return new WorkerFaceEnrollment(workerId, embedding, ACCEPTABLE_QUALITY, 0.95, null,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "127.0.0.1");
    }

    /** Happy-path plumbing for the new photo being enrolled: acceptable/live, no prior row for this worker, save() echoes back whatever was passed to it. */
    private void stubNewPhotoEmbedding(float[] embedding) {
        when(faceAuthPort.embed(any(), anyString()))
                .thenReturn(new FaceEmbeddingResult(embedding, ACCEPTABLE_QUALITY, true, 0.97));
        when(enrollments.findByWorkerId(any())).thenReturn(Optional.empty());
        when(enrollments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enroll_whenNewPhotoConfidentlyMatchesADifferentEnrolledWorker_throwsConflictAndPersistsNothing() {
        UUID newWorkerId = UUID.randomUUID();
        UUID conflictingWorkerId = UUID.randomUUID();
        stubNewPhotoEmbedding(vectorWithCosine(1.0));
        when(enrollments.findByExcludedFromMatchingAtIsNull())
                .thenReturn(List.of(enrollmentWithEmbedding(conflictingWorkerId, vectorWithCosine(0.95))));

        assertThatThrownBy(() -> service.enroll(newWorkerId, "face.jpg", "image/jpeg", PHOTO_BYTES, true, "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(conflictingWorkerId.toString())
                .hasMessageContaining("already enrolled");
        verify(enrollments, never()).save(any());
    }

    @Test
    void enroll_whenNewPhotoDoesNotMatchAnyOtherWorker_succeeds() {
        UUID newWorkerId = UUID.randomUUID();
        UUID otherWorkerId = UUID.randomUUID();
        stubNewPhotoEmbedding(vectorWithCosine(1.0));
        when(enrollments.findByExcludedFromMatchingAtIsNull())
                .thenReturn(List.of(enrollmentWithEmbedding(otherWorkerId, vectorWithCosine(0.1))));

        FaceEnrollmentDto dto = service.enroll(newWorkerId, "face.jpg", "image/jpeg", PHOTO_BYTES, true, "127.0.0.1");

        assertThat(dto.enrolled()).isTrue();
    }

    @Test
    void enroll_reEnrollingSelf_isNeverTreatedAsADuplicateOfOwnPriorPhoto() {
        UUID workerId = UUID.randomUUID();
        stubNewPhotoEmbedding(vectorWithCosine(1.0));
        // Own prior enrollment would score a perfect 1.0 if it were ever compared - rejectIfDuplicate
        // must exclude workerId from the comparison set itself in Java, since
        // findByExcludedFromMatchingAtIsNull() has no way to know which worker is being re-enrolled.
        when(enrollments.findByExcludedFromMatchingAtIsNull())
                .thenReturn(List.of(enrollmentWithEmbedding(workerId, vectorWithCosine(1.0))));

        assertThatCode(() -> service.enroll(workerId, "face.jpg", "image/jpeg", PHOTO_BYTES, true, "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void enroll_whenNoOtherEnrollmentsExist_succeedsWithoutThrowing() {
        UUID workerId = UUID.randomUUID();
        stubNewPhotoEmbedding(vectorWithCosine(1.0));
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of());

        FaceEnrollmentDto dto = service.enroll(workerId, "face.jpg", "image/jpeg", PHOTO_BYTES, true, "127.0.0.1");

        assertThat(dto.enrolled()).isTrue();
    }

    @Test
    void enroll_duplicateThreshold_exactlyAtThresholdCountsAsADuplicate() {
        UUID newWorkerId = UUID.randomUUID();
        UUID conflictingWorkerId = UUID.randomUUID();
        stubNewPhotoEmbedding(vectorWithCosine(1.0));
        when(enrollments.findByExcludedFromMatchingAtIsNull())
                .thenReturn(List.of(enrollmentWithEmbedding(conflictingWorkerId, vectorWithCosine(0.5))));

        assertThatThrownBy(() -> service.enroll(newWorkerId, "face.jpg", "image/jpeg", PHOTO_BYTES, true, "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }
}
