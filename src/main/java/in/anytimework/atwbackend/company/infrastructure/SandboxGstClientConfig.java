package in.anytimework.atwbackend.company.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the configured {@code RestClient} {@link SandboxGstVerificationAdapter} uses - kept separate
 * from the adapter itself, same split as {@code kyc.infrastructure.SandboxClientConfig}/
 * {@code worker.infrastructure.SandboxBankClientConfig}. No default {@code Authorization}/API-key
 * header here, same reasoning as those two: Sandbox.co.in needs a freshly-fetched bearer token per
 * call (see the adapter's own Javadoc), so that header is added per-request instead.
 */
@Configuration
@ConditionalOnProperty(name = "atw.gst-verification.provider", havingValue = "sandbox")
class SandboxGstClientConfig {

    @Bean
    RestClient sandboxGstRestClient(RestClient.Builder builder, SandboxGstProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
