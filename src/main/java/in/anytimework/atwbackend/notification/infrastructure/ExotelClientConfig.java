package in.anytimework.atwbackend.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Builds the configured {@code RestClient} {@link ExotelSmsSender} uses - kept separate from the
 * adapter itself (same split as {@code FaceAuthClientConfig}/{@code PythonFaceAuthAdapter}) so the
 * adapter has no construction logic of its own to interfere with binding a test double to a plain
 * builder.
 */
@Configuration
@ConditionalOnProperty(name = "atw.sms.provider", havingValue = "exotel")
class ExotelClientConfig {

    @Bean
    RestClient exotelRestClient(RestClient.Builder builder, ExotelSmsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        String credentials = properties.apiKey() + ":" + properties.apiToken();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return builder
                .baseUrl("https://" + properties.subdomain())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", basicAuth)
                .build();
    }
}
