package in.anytimework.atwbackend.faceauth.application;

/**
 * Raw result of turning a photo into a face embedding, exactly as {@link FaceAuthPort#embed} returns
 * it - no ATW-specific policy applied yet. Whether {@code quality().acceptable()}/{@code live()} being
 * false should block enrollment is {@link FaceAuthService}'s decision, not the port's or the vendor's.
 */
public record FaceEmbeddingResult(
        float[] embedding,
        FaceQualitySnapshot quality,
        boolean live,
        double livenessConfidence
) {
}
