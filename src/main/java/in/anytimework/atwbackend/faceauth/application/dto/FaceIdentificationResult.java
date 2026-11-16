package in.anytimework.atwbackend.faceauth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a 1:N face-identification call. {@code matched=false} is a normal result (nothing
 * clears the threshold, or the top two candidates are too close to call with confidence - see
 * {@code ambiguous}) - never an exception. {@code topMatches} is included for admin review/debugging
 * even when there's no confident match; only {@code matched}/{@code workerId} are the actual decision.
 */
@Schema(description = "Result of comparing a probe photo against every currently-enrolled worker.")
public record FaceIdentificationResult(
        @Schema(description = "Whether a confident, unambiguous match was found.") boolean matched,
        @Schema(description = "The matched worker's id, or null if matched=false.", nullable = true) UUID workerId,
        @Schema(description = "The best (highest) similarity score found, regardless of whether it cleared "
                + "the threshold.", example = "0.87") double bestScore,
        @Schema(description = "True when the top two candidates scored too close together to confidently "
                + "pick one, even if the best score alone would have cleared the threshold - matched is "
                + "forced false in that case.") boolean ambiguous,
        @Schema(description = "Up to the top 5 ranked candidates, for admin review/debugging - not just "
                + "the one that matched (if any).") List<FaceIdentificationCandidate> topMatches
) {
}
