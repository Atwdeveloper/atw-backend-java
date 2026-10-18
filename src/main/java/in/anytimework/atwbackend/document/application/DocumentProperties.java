package in.anytimework.atwbackend.document.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Upload policy for documents, bound from {@code atw.documents.*}.
 *
 * @param maxSizeBytes        largest accepted file size
 * @param allowedContentTypes MIME types the module will accept
 */
@ConfigurationProperties(prefix = "atw.documents")
public record DocumentProperties(long maxSizeBytes, List<String> allowedContentTypes) {
}
