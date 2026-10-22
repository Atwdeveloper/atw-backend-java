package in.anytimework.atwbackend.document.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Upload policy for documents, bound from {@code atw.documents.*}.
 *
 * @param maxSizeBytes             largest accepted file size
 * @param allowedContentTypes      MIME types the module will accept
 * @param reconciliationStaleAfter how long a row may sit {@code PENDING} before the reconciliation
 *                                 job treats it as stuck and resolves it
 * @param reconciliationInterval   how often the reconciliation job runs
 */
@ConfigurationProperties(prefix = "atw.documents")
public record DocumentProperties(
        long maxSizeBytes,
        List<String> allowedContentTypes,
        Duration reconciliationStaleAfter,
        Duration reconciliationInterval) {
}
