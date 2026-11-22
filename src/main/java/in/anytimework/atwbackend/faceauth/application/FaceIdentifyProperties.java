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
 * {@code FaceAuthService::identifyBest} (a "too close to call" guard against confidently picking
 * between two similar-scoring faces). Operator-tunable without a code change: the "right" values
 * depend on the actual embedding model a real vendor returns, which this codebase cannot know in
 * advance.</p>
 *
 * <p>Defaults below (0.50/0.05) are legacy's own real, production-validated values (see
 * {@code config/services.php} in the legacy reference app) - not a guess. Legacy's history: 0.45 was
 * tried on synthetic data and reverted to 0.50 the same day once a real production sample scored
 * 0.696, comfortably clear of 0.50. This codebase's own first port of this threshold used 0.6, which
 * does not match legacy's actual validated value - corrected 2026-11-17 once that was discovered.
 * Neither value is calibrated against this specific deployment's own real spoof/impostor data yet.</p>
 */
@ConfigurationProperties(prefix = "atw.face-auth.identify")
public record FaceIdentifyProperties(Double threshold, Double margin) {

    public FaceIdentifyProperties {
        if (threshold == null) {
            threshold = 0.5;
        }
        if (margin == null) {
            margin = 0.05;
        }
    }
}
