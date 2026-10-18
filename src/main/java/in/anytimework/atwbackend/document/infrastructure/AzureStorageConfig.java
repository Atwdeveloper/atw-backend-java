package in.anytimework.atwbackend.document.infrastructure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds the Azure Blob client. If a connection string is configured (local Azurite or key-based
 * Azure), it is used directly; otherwise the client authenticates to {@code endpoint} with
 * {@link DefaultAzureCredentialBuilder} (managed identity in Azure). Building the client makes no
 * network call, so the application still starts when storage is unreachable.
 */
@Configuration
public class AzureStorageConfig {

    @Bean
    public BlobServiceClient blobServiceClient(StorageProperties properties) {
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();
        if (StringUtils.hasText(properties.connectionString())) {
            builder.connectionString(properties.connectionString());
        } else {
            builder.endpoint(properties.endpoint())
                    .credential(new DefaultAzureCredentialBuilder().build());
        }
        return builder.buildClient();
    }
}
