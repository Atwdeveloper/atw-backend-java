package in.anytimework.atwbackend.faceauth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** One ranked candidate from a {@code FaceAuthService.identify} call - a raw similarity score, not itself a verdict. */
@Schema(description = "One candidate worker considered during identification, ranked by similarity.")
public record FaceIdentificationCandidate(
        @Schema(description = "Candidate worker id.") UUID workerId,
        @Schema(description = "Cosine similarity to the probe photo, from -1 (opposite) to 1 (identical). "
                + "Not itself pass/fail - see FaceIdentificationResult.matched for the actual decision.",
                example = "0.87") double score
) {
}
