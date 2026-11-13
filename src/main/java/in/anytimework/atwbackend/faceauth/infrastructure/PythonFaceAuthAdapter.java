package in.anytimework.atwbackend.faceauth.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.anytimework.atwbackend.faceauth.application.FaceAuthPort;
import in.anytimework.atwbackend.faceauth.application.FaceEmbeddingResult;
import in.anytimework.atwbackend.faceauth.application.FaceQualitySnapshot;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calls the real, separately-hosted Python face-auth service (repo CLAUDE.md §6). Field names below
 * ({@code face_width_px}, {@code is_acceptable}, {@code is_live}, ...) are inferred from the legacy
 * Laravel client's calling code, not independently verified against a live instance of today's
 * service - if the real contract differs, the fix is confined entirely to this one class's private
 * wire-format records.
 *
 * <p>Response convention: the service always answers HTTP 200; success/failure is read from a
 * {@code status} field inside the JSON body, not the status code - handled explicitly below rather
 * than relying on {@code RestClient}'s default 2xx/4xx/5xx error handling.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.face-auth.provider", havingValue = "python")
class PythonFaceAuthAdapter implements FaceAuthPort {

    private final RestClient restClient;

    /**
     * Takes an already-fully-configured {@code RestClient} (built by {@link FaceAuthClientConfig}, not
     * constructed here) specifically so this class has no construction logic of its own to get in the
     * way of a test binding {@code MockRestServiceServer} to a plain builder and handing the resulting
     * client straight in - see {@code PythonFaceAuthAdapterTest}.
     */
    PythonFaceAuthAdapter(RestClient faceAuthRestClient) {
        this.restClient = faceAuthRestClient;
    }

    @Override
    public FaceEmbeddingResult embed(byte[] imageBytes, String contentType) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("image", new ByteArrayResource(imageBytes))
                .contentType(MediaType.parseMediaType(contentType));

        EmbedWireResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/embed")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(EmbedWireResponse.class);
        } catch (ResourceAccessException e) {
            String message = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                    .toLowerCase(Locale.ROOT);
            if (message.contains("timed out") || message.contains("timeout")) {
                throw new BusinessRuleException("Face-auth service timed out - please try again.");
            }
            throw new BusinessRuleException("Face-auth service is unreachable - please try again shortly.");
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException("Face-auth service rejected the request: " + e.getStatusText());
        }

        if (response == null || !"success".equalsIgnoreCase(response.status())) {
            throw new BusinessRuleException(describeFailure(response));
        }
        if (response.embedding() == null || response.quality() == null || response.liveness() == null) {
            throw new BusinessRuleException("Face-auth service returned an incomplete response.");
        }

        float[] embedding = new float[response.embedding().size()];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = response.embedding().get(i);
        }
        FaceQualitySnapshot quality = new FaceQualitySnapshot(
                response.quality().faceWidthPx(), response.quality().sharpness(), response.quality().brightness(),
                response.quality().isAcceptable(),
                response.quality().reasons() == null ? List.of() : response.quality().reasons());
        return new FaceEmbeddingResult(embedding, quality, response.liveness().isLive(), response.liveness().confidence());
    }

    private static String describeFailure(EmbedWireResponse response) {
        if (response == null || response.detail() == null) {
            return "Face-auth service returned an unexpected response.";
        }
        Object detail = response.detail();
        if (detail instanceof String s) {
            return s;
        }
        if (detail instanceof Map<?, ?> map && map.get("error") != null) {
            return String.valueOf(map.get("error"));
        }
        return "Face-auth service reported a failure.";
    }

    private record EmbedWireResponse(
            String status, List<Float> embedding, QualityWire quality, LivenessWire liveness, Object detail
    ) {
    }

    private record QualityWire(
            @JsonProperty("face_width_px") int faceWidthPx,
            double sharpness,
            double brightness,
            @JsonProperty("is_acceptable") boolean isAcceptable,
            List<String> reasons
    ) {
    }

    private record LivenessWire(@JsonProperty("is_live") boolean isLive, double confidence) {
    }
}
