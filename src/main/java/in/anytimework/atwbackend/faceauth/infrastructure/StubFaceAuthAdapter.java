package in.anytimework.atwbackend.faceauth.infrastructure;

import in.anytimework.atwbackend.faceauth.application.FaceAuthPort;
import in.anytimework.atwbackend.faceauth.application.FaceEmbeddingResult;
import in.anytimework.atwbackend.faceauth.application.FaceQualitySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Development/demo face-auth adapter: no real service account/deployment reachable from here yet.
 * Active by default ({@code atw.face-auth.provider=stub}). Returns a fixed-size, randomized but always
 * acceptable-and-live embedding for any input, so enrollment can be exercised end-to-end locally and
 * in tests without a live Python service.
 */
@Component
@ConditionalOnProperty(name = "atw.face-auth.provider", havingValue = "stub", matchIfMissing = true)
class StubFaceAuthAdapter implements FaceAuthPort {

    private static final Logger log = LoggerFactory.getLogger(StubFaceAuthAdapter.class);
    private static final int EMBEDDING_DIMENSIONS = 128;
    private final SecureRandom random = new SecureRandom();

    @Override
    public FaceEmbeddingResult embed(byte[] imageBytes, String contentType) {
        log.info("[STUB FACE AUTH] Generating a fake embedding for a {}-byte {} image (reference {})",
                imageBytes.length, contentType, UUID.randomUUID());
        float[] embedding = new float[EMBEDDING_DIMENSIONS];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat() * 2 - 1;
        }
        FaceQualitySnapshot quality = new FaceQualitySnapshot(400, 0.9, 0.5, true, List.of());
        return new FaceEmbeddingResult(embedding, quality, true, 0.95);
    }
}
