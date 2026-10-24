package in.anytimework.atwbackend.document.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Metadata view of a stored document returned to callers. */
public record DocumentDto(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String category,
        UUID ownerId,
        Instant createdAt
) {
}
