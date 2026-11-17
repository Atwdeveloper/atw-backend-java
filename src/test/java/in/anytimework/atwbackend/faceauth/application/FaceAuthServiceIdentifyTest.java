package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceIdentificationResult;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollment;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for {@code FaceAuthServiceImpl.identify} - mirrors
 * {@code WorkerServiceStatusGuardTest}'s style for business-rule coverage a real database/vendor
 * can't deterministically exercise. {@link in.anytimework.atwbackend.faceauth.infrastructure.StubFaceAuthAdapter}
 * returns a genuinely random embedding on every call, so the threshold/ambiguity decision logic can
 * only be verified with a controlled, mocked {@link FaceAuthPort} - this fills that gap;
 * {@link in.anytimework.atwbackend.faceauth.FaceAuthServiceIntegrationTest} covers the real
 * enroll/remove/getEnrollment wiring against Postgres.
 *
 * <p>Every embedding below is a 128-dim unit vector in the (index 0, index 1) plane - zero everywhere
 * else - so the cosine similarity between the probe and any candidate is exactly the dot product of
 * their first two components, chosen to land on a specific, known score.</p>
 */
class FaceAuthServiceIdentifyTest {

    private final WorkerFaceEnrollmentRepository enrollments = mock(WorkerFaceEnrollmentRepository.class);
    private final FaceAuthPort faceAuthPort = mock(FaceAuthPort.class);
    private final FaceEnrollmentPhotoSupport photoSupport = mock(FaceEnrollmentPhotoSupport.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final FaceIdentifyProperties identifyProperties = new FaceIdentifyProperties(0.6, 0.05);
    private final FaceDuplicateProperties duplicateProperties = new FaceDuplicateProperties(0.5);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneOffset.UTC);

    private final FaceAuthServiceImpl service = new FaceAuthServiceImpl(
            enrollments, faceAuthPort, photoSupport, documentService, identifyProperties, duplicateProperties, clock);

    private static final byte[] PROBE_BYTES = {1, 2, 3};
    private static final FaceQualitySnapshot ACCEPTABLE_QUALITY = new FaceQualitySnapshot(400, 0.9, 0.5, true, List.of());

    /** A 128-dim unit vector with cosine = cos0 against (1,0,0,...) and sin0 = sqrt(1-cos0^2) in the next slot. */
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

    private void stubProbeEmbedding(float[] embedding) {
        when(faceAuthPort.embed(any(), anyString()))
                .thenReturn(new FaceEmbeddingResult(embedding, ACCEPTABLE_QUALITY, true, 0.97));
    }

    @Test
    void identify_withNoOneEnrolled_throwsBusinessRuleException() {
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of());

        assertThatThrownBy(() -> service.identify(PROBE_BYTES, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no one enrolled");
    }

    @Test
    void identify_aClearBestMatch_isConfidentAndNotAmbiguous() {
        UUID matching = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of(
                enrollmentWithEmbedding(matching, vectorWithCosine(1.0)),   // score 1.0 vs probe
                enrollmentWithEmbedding(other, vectorWithCosine(0.0))));    // score 0.0 vs probe - orthogonal
        stubProbeEmbedding(vectorWithCosine(1.0)); // probe == (1,0,0,...)

        FaceIdentificationResult result = service.identify(PROBE_BYTES, "image/jpeg");

        assertThat(result.matched()).isTrue();
        assertThat(result.workerId()).isEqualTo(matching);
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.bestScore()).isCloseTo(1.0, within(1e-6));
        assertThat(result.topMatches()).hasSize(2);
    }

    @Test
    void identify_whenBestScoreIsBelowThreshold_isNotMatchedButNotAnError() {
        UUID candidate = UUID.randomUUID();
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of(enrollmentWithEmbedding(candidate, vectorWithCosine(0.3))));
        stubProbeEmbedding(vectorWithCosine(1.0));

        FaceIdentificationResult result = service.identify(PROBE_BYTES, "image/jpeg");

        assertThat(result.matched()).isFalse();
        assertThat(result.workerId()).isNull();
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.bestScore()).isCloseTo(0.3, within(1e-6));
    }

    @Test
    void identify_whenTopTwoCandidatesAreTooClose_isAmbiguousEvenThoughBestClearsThreshold() {
        UUID best = UUID.randomUUID();
        UUID runnerUp = UUID.randomUUID();
        // 0.70 vs 0.68 - both clear the 0.6 threshold, but the 0.02 gap is under the 0.05 margin.
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of(
                enrollmentWithEmbedding(best, vectorWithCosine(0.70)),
                enrollmentWithEmbedding(runnerUp, vectorWithCosine(0.68))));
        stubProbeEmbedding(vectorWithCosine(1.0));

        FaceIdentificationResult result = service.identify(PROBE_BYTES, "image/jpeg");

        assertThat(result.ambiguous()).isTrue();
        assertThat(result.matched()).isFalse();
        assertThat(result.workerId()).isNull();
    }

    @Test
    void identify_topMatches_isCappedAtFiveAndSortedDescending() {
        List<WorkerFaceEnrollment> sixCandidates = List.of(
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.1)),
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.9)),
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.2)),
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.3)),
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.4)),
                enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(0.5)));
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(sixCandidates);
        stubProbeEmbedding(vectorWithCosine(1.0));

        FaceIdentificationResult result = service.identify(PROBE_BYTES, "image/jpeg");

        assertThat(result.topMatches()).hasSize(5);
        assertThat(result.topMatches().get(0).score()).isCloseTo(0.9, within(1e-6));
        assertThat(result.topMatches()).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    void identify_whenProbeQualityIsUnacceptable_throwsBusinessRuleException() {

        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of(enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(1.0))));
        FaceQualitySnapshot poor = new FaceQualitySnapshot(50, 0.1, 0.1, false, List.of("face too small"));
        when(faceAuthPort.embed(any(), anyString()))
                .thenReturn(new FaceEmbeddingResult(vectorWithCosine(1.0), poor, true, 0.9));

        assertThatThrownBy(() -> service.identify(PROBE_BYTES, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("face too small");
    }

    @Test
    void identify_whenProbeLivenessFails_throwsBusinessRuleException() {
        when(enrollments.findByExcludedFromMatchingAtIsNull()).thenReturn(List.of(enrollmentWithEmbedding(UUID.randomUUID(), vectorWithCosine(1.0))));
        when(faceAuthPort.embed(any(), anyString()))
                .thenReturn(new FaceEmbeddingResult(vectorWithCosine(1.0), ACCEPTABLE_QUALITY, false, 0.2));

        assertThatThrownBy(() -> service.identify(PROBE_BYTES, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Liveness check failed");
    }
}
