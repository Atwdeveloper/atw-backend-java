package in.anytimework.atwbackend.faceauth.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the configured {@code RestClient} {@link PythonFaceAuthAdapter} uses - kept separate from the
 * adapter itself so the adapter has no construction logic of its own to interfere with binding
 * {@code MockRestServiceServer} to a plain builder in a test (see {@code PythonFaceAuthAdapterTest}).
 */
@Configuration
@ConditionalOnProperty(name = "atw.face-auth.provider", havingValue = "python")
class FaceAuthClientConfig {

    @Bean
    RestClient faceAuthRestClient(RestClient.Builder builder, FaceAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-API-Key", properties.apiKey())
                .build();
    }
}
