package in.anytimework.atwbackend.kyc.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the configured {@code RestClient} {@link SandboxAadhaarKycAdapter} uses - kept separate from
 * the adapter itself (same split as {@code FaceAuthClientConfig}/{@code PythonFaceAuthAdapter}) so the
 * adapter has no construction logic of its own to interfere with binding a test double to a plain
 * builder. No default {@code Authorization}/API-key header here (unlike the other two adapters in this
 * codebase) because Sandbox.co.in needs a freshly-fetched bearer token per call (see the adapter's own
 * Javadoc for why it re-authenticates every time rather than caching), so those headers are added
 * per-request instead of once at the client level.
 */
@Configuration
@ConditionalOnProperty(name = "atw.aadhaar-kyc.provider", havingValue = "sandbox")
class SandboxClientConfig {

    @Bean
    RestClient sandboxRestClient(RestClient.Builder builder, SandboxKycProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
