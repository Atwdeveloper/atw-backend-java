package in.anytimework.atwbackend.notification.infrastructure;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.identity.DefaultAzureCredentialBuilder;
import in.anytimework.atwbackend.notification.application.EmailMessage;
import in.anytimework.atwbackend.notification.application.EmailSender;
import in.anytimework.atwbackend.notification.application.MailProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Azure Communication Services email transport. Active when {@code atw.mail.provider=azure}.
 * Uses a connection string when provided, otherwise authenticates to the endpoint with managed
 * identity. This is the only class that knows about the Azure email SDK.
 */
@Component
@ConditionalOnProperty(name = "atw.mail.provider", havingValue = "azure")
public class AzureEmailSender implements EmailSender {

    private final EmailClient client;
    private final String fromAddress;

    public AzureEmailSender(MailProperties properties,
            @Value("${atw.azure.managed-identity-client-id:}") String managedIdentityClientId) {
        this.fromAddress = properties.fromAddress();
        EmailClientBuilder builder = new EmailClientBuilder();
        if (StringUtils.hasText(properties.azureConnectionString())) {
            builder.connectionString(properties.azureConnectionString());
        } else {
            DefaultAzureCredentialBuilder credentialBuilder = new DefaultAzureCredentialBuilder();
            if (StringUtils.hasText(managedIdentityClientId)) {
                credentialBuilder.managedIdentityClientId(managedIdentityClientId);
            }
            builder.endpoint(properties.azureEndpoint()).credential(credentialBuilder.build());
        }
        this.client = builder.buildClient();
    }

    @Override
    public String send(EmailMessage message) {
        com.azure.communication.email.models.EmailMessage azureMessage =
                new com.azure.communication.email.models.EmailMessage()
                        .setSenderAddress(fromAddress)
                        .setToRecipients(message.to())
                        .setSubject(message.subject())
                        .setBodyHtml(message.htmlBody());
        EmailSendResult result = client.beginSend(azureMessage).getFinalResult();
        return result.getId();
    }
}
