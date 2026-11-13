package in.anytimework.atwbackend.shared.security;

import in.anytimework.atwbackend.shared.exception.ValidationException;

import java.util.Locale;
import java.util.Map;

/**
 * Verifies a file's actual leading bytes match its declared content type, for the image types this
 * application accepts (PNG/JPEG/WEBP) - guards against a spoofed {@code Content-Type} (e.g. an
 * HTML/SVG payload declared as {@code image/png}) being stored and later served back as-is to a
 * browser via a signed URL, a stored-XSS vector. SVG is deliberately never an accepted type anywhere
 * in this application for the same reason - it can embed {@code <script>}.
 *
 * <p>No-op for anything that isn't one of those three recognized image types (including PDF, whose
 * own residual risk wherever it's accepted for KYC-grade uploads is a separately-accepted tradeoff)
 * and for empty content (left to the caller's own "content must not be empty" check, which produces
 * a clearer message than this class could).</p>
 *
 * <p>Used by {@code worker.application.WorkerProfilePhotoSupport} and
 * {@code faceauth.application.FaceEnrollmentPhotoSupport}. {@code masterdata.application
 * .MasterDataImageSupport} predates this extraction and still carries its own copy of the same
 * logic - left alone rather than refactored as part of this change, since that module's working,
 * already-shipped code wasn't otherwise being touched; worth consolidating in a focused follow-up.</p>
 */
public final class ImageMagicBytes {

    private static final Map<String, byte[]> MAGIC_BYTES_BY_CONTENT_TYPE = Map.of(
            "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    );
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_FOURCC = {0x57, 0x45, 0x42, 0x50};
    private static final int WEBP_FOURCC_OFFSET = 8;

    private ImageMagicBytes() {
    }

    /** @throws ValidationException if contentType is a recognized image type but content's bytes don't match it. */
    public static void verifyIfRecognizedImageType(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            return;
        }
        String normalized = contentType == null ? null : contentType.toLowerCase(Locale.ROOT).trim();
        if ("image/webp".equals(normalized)) {
            if (!matchesAt(content, 0, RIFF) || !matchesAt(content, WEBP_FOURCC_OFFSET, WEBP_FOURCC)) {
                throw new ValidationException("File content does not match declared content type image/webp");
            }
            return;
        }
        byte[] expectedMagic = normalized == null ? null : MAGIC_BYTES_BY_CONTENT_TYPE.get(normalized);
        if (expectedMagic != null && !matchesAt(content, 0, expectedMagic)) {
            throw new ValidationException("File content does not match declared content type " + normalized);
        }
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
