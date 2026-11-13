package in.anytimework.atwbackend.faceauth.domain;

import in.anytimework.atwbackend.faceauth.application.FaceQualitySnapshot;
import in.anytimework.atwbackend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A worker's enrolled face template - one row per worker (real FK, {@code ON DELETE CASCADE}, matching
 * the same single-owner-table precedent as {@code worker_kyc_profile}). Re-enrollment fully overwrites
 * the previous template (same behavior as legacy): there is no history of past templates, since only
 * the current one is ever meaningful for a future match.
 *
 * <p>{@code embedding} is the raw 128-float vector the face-auth service returns - stored as a plain
 * Postgres {@code real[]}, not the {@code pgvector} extension's native vector type: see this module's
 * migration for why (no search happens in this phase, and pgvector has no straightforward Windows
 * installer for this project's local dev Postgres).</p>
 *
 * <p>{@code qualitySnapshot} references {@code faceauth.application.FaceQualitySnapshot} - a deliberate
 * domain-depends-on-application exception, the same pragmatic shape as
 * {@code kyc.domain.WorkerKycProfile} importing {@code kyc.application.KycStatus}: this type is
 * genuinely part of the module's public contract (returned by {@code FaceAuthPort}), not an internal
 * detail invented here.</p>
 */
@Getter
@Entity
@Table(name = "worker_face_enrollment")
public class WorkerFaceEnrollment extends BaseEntity {

    @Column(name = "worker_id", nullable = false, unique = true)
    private UUID workerId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "embedding", nullable = false, columnDefinition = "real[]")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_snapshot", nullable = false, columnDefinition = "jsonb")
    private FaceQualitySnapshot qualitySnapshot;

    @Column(name = "liveness_confidence", nullable = false)
    private double livenessConfidence;

    @Column(name = "enroll_photo_document_id")
    private UUID enrollPhotoDocumentId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "consent_ip", length = 45)
    private String consentIp;

    protected WorkerFaceEnrollment() {
        // required by JPA
    }

    public WorkerFaceEnrollment(UUID workerId, float[] embedding, FaceQualitySnapshot qualitySnapshot,
                                double livenessConfidence, UUID enrollPhotoDocumentId, Instant enrolledAt,
                                Instant consentGivenAt, String consentIp) {
        this.workerId = workerId;
        reEnroll(embedding, qualitySnapshot, livenessConfidence, enrollPhotoDocumentId, enrolledAt, consentGivenAt, consentIp);
    }

    /** Full overwrite - see class Javadoc for why no history of past templates is kept. */
    public void reEnroll(float[] embedding, FaceQualitySnapshot qualitySnapshot, double livenessConfidence,
                         UUID enrollPhotoDocumentId, Instant enrolledAt, Instant consentGivenAt, String consentIp) {
        this.embedding = embedding;
        this.qualitySnapshot = qualitySnapshot;
        this.livenessConfidence = livenessConfidence;
        this.enrollPhotoDocumentId = enrollPhotoDocumentId;
        this.enrolledAt = enrolledAt;
        this.consentGivenAt = consentGivenAt;
        this.consentIp = consentIp;
    }
}
