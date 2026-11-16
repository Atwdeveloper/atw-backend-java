package in.anytimework.atwbackend.faceauth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Match thresholds for {@code FaceAuthService.identify} (1:N) - deliberately its own properties
 * record in the {@code application} layer, not folded into {@code faceauth.infrastructure}'s
 * {@code FaceAuthProperties} (vendor connection details): thresholds are an application-level policy
 * decision (see {@link FaceAuthPort}'s own Javadoc on why matching/thresholds live here, not with any
 * vendor adapter), and application code must never depend on infrastructure.
 *
 * <p>The best candidate must score at least {@code threshold} AND beat the runner-up by at least
 * {@code margin} to count as a confident match - same two-threshold shape as legacy's own
 * face-identify test tool (a "too close to call" guard against confidently picking between two
 * similar-scoring faces). Operator-tunable without a code change: the "right" values depend on the
 * actual embedding model a real vendor returns, which this codebase cannot know in advance - the
 * defaults below are starting points only, not calibrated against a live provider.</p>
 */
@ConfigurationProperties(prefix = "atw.face-auth.identify")
public record FaceIdentifyProperties(Double threshold, Double margin) {

    public FaceIdentifyProperties {
        if (threshold == null) {
            threshold = 0.6;
        }
        if (margin == null) {
            margin = 0.05;
        }
    }
}
