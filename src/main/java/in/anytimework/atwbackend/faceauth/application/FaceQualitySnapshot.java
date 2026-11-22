package in.anytimework.atwbackend.faceauth.application;

import java.util.List;

/**
 * The face-auth service's own image-quality assessment at enrollment time - persisted as-is (see
 * {@code faceauth.domain.WorkerFaceEnrollment.qualitySnapshot}, stored as JSONB via Hibernate) for
 * later audit/debugging ("why did this enrollment get flagged"), not re-derived.
 *
 * <p>Deliberately has no Jackson wire-format annotations of its own - {@code faceauth.infrastructure}'s
 * adapter maps the vendor's actual wire shape (snake_case field names) into this clean record, so a
 * future vendor field-name change never touches already-persisted rows' JSON key casing or this
 * module's own contract type.</p>
 */
public record FaceQualitySnapshot(
        int faceWidthPx,
        double sharpness,
        double brightness,
        boolean acceptable,
        List<String> reasons
) {
}
