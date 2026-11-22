package in.anytimework.atwbackend.faceauth.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;

/**
 * Port to the external face-recognition service (a separately-hosted Python microservice - see repo
 * CLAUDE.md §6: "Secure file uploads and communication with the separate Python face-auth service").
 * Deliberately does ONE thing - turn a photo into a face embedding plus the vendor's own quality/
 * liveness assessment - mirroring how the real service itself is scoped: matching, thresholds, and
 * duplicate-detection are an application-level policy decision (see {@link FaceAuthService}), never
 * the port's or the vendor's concern. Swapping vendors later is one new adapter class in
 * {@code faceauth.infrastructure} plus a config change - never a change to any caller of this interface.
 */
public interface FaceAuthPort {

    /**
     * @throws BusinessRuleException if the service is unreachable, times out, or rejects the request -
     *         surfaced to the admin as a clean, retryable error, not a crash. A poor-quality or
     *         non-live photo is NOT an exception here - it comes back as a normal result with
     *         {@code quality().acceptable()}/{@code live()} set to false, for the caller to act on.
     */
    FaceEmbeddingResult embed(byte[] imageBytes, String contentType);
}
