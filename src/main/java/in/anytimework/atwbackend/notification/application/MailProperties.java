package in.anytimework.atwbackend.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email settings, bound from {@code atw.mail.*}.
 *
 * @param provider              "log" (dev), "smtp", or "azure"
 * @param fromAddress           sender address
 * @param fromName              sender display name (e.g. "Anytime Work")
 * @param azureConnectionString connection string for Azure Communication Services (key-based)
 * @param azureEndpoint         endpoint for Azure Communication Services (managed identity)
 */
@ConfigurationProperties(prefix = "atw.mail")
public record MailProperties(String provider, String fromAddress, String fromName,
                              String azureConnectionString, String azureEndpoint) {
}
