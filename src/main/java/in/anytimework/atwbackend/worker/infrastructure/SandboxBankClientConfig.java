package in.anytimework.atwbackend.worker.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the configured {@code RestClient} {@link SandboxBankVerificationAdapter} uses - kept separate
 * from the adapter itself, same split as {@code kyc}'s {@code SandboxClientConfig}/
 * {@code SandboxAadhaarKycAdapter}, so the adapter has no construction logic of its own to interfere
 * with binding a test double to a plain builder.
 */
@Configuration
@ConditionalOnProperty(name = "atw.bank-verification.provider", havingValue = "sandbox")
class SandboxBankClientConfig {

    @Bean
    RestClient sandboxBankRestClient(RestClient.Builder builder, SandboxBankProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
