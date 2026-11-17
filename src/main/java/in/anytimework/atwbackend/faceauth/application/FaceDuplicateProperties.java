package in.anytimework.atwbackend.faceauth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Enrollment-time duplicate-face threshold - deliberately its own properties record, separate from
 * {@link FaceIdentifyProperties}, even though both currently default to the same value. Legacy keeps
 * {@code duplicate_threshold} and {@code identify_threshold} as two independent config keys for the
 * same reason this does: "is this the same person" is being asked at two different moments
 * (enrollment-time vs. a later identify lookup) and may need different answers once real calibration
 * data exists for either one.
 *
 * <p>Unlike {@link FaceIdentifyProperties}, there is no margin/ambiguity concept here - a duplicate
 * check only ever asks "does the single best match across every other enrolled worker clear the
 * threshold", the same shape as legacy's {@code FaceAuthService::findDuplicateFace} - it never needs
 * to compare the best candidate against a runner-up the way a 1:N identify decision does.</p>
 */
@ConfigurationProperties(prefix = "atw.face-auth.duplicate")
public record FaceDuplicateProperties(Double threshold) {

    public FaceDuplicateProperties {
        if (threshold == null) {
            threshold = 0.5;
        }
    }
}
