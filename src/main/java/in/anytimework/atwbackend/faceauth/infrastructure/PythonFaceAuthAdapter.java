package in.anytimework.atwbackend.faceauth.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Calls the real, separately-hosted Python face-auth service (repo CLAUDE.md §6; see project memory
 * "python-face-auth-service-location" for exactly which service and where its source lives).
 *
 * <p>Response convention, confirmed 2026-11-17 by reading the real service's own {@code app/main.py} -
 * an earlier version of this class had this wrong, inferred from the legacy Laravel client instead of
 * the Python service itself: a successful call is a plain HTTP 200 whose body IS the embed result
 * directly ({@code embedding}/{@code model}/{@code dim}/{@code quality}/{@code liveness}) - there is
 * no top-level {@code status} field on success, ever. Every failure is a real non-2xx HTTP status
 * (400 empty_upload/invalid_image, 401 invalid_api_key, 413 image_too_large, 422 no_face_detected/
 * multiple_faces_detected, 503 service_unavailable) whose body is {@code {"detail": {"error": "<code>",
 * ...extra}}} - the real service's own module docstring states {@code detail} is always a structured
 * object, never a bare string, but this still tolerates a bare string defensively (matching legacy's
 * own defensive handling) in case a reverse proxy or load balancer ever answers instead of the service
 * itself.</p>
 */
@Component
@ConditionalOnProperty(name = "atw.face-auth.provider", havingValue = "python")
class PythonFaceAuthAdapter implements FaceAuthPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Takes an already-fully-configured {@code RestClient} (built by {@link FaceAuthClientConfig}, not
     * constructed here) specifically so this class has no construction logic of its own to get in the
     * way of a test binding {@code MockRestServiceServer} to a plain builder and handing the resulting
     * client straight in - see {@code PythonFaceAuthAdapterTest}. {@code objectMapper} only parses
     * error bodies (see {@link #describeFailure}) - the success body is deserialized by the
     * {@code RestClient} itself via its own configured message converters.
     */
    PythonFaceAuthAdapter(RestClient faceAuthRestClient, ObjectMapper objectMapper) {
        this.restClient = faceAuthRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public FaceEmbeddingResult embed(byte[] imageBytes, String contentType) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("image", new ByteArrayResource(imageBytes))
                .contentType(MediaType.parseMediaType(contentType));

        EmbedSuccessResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/embed")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart.build())
                    .retrieve()
                    .body(EmbedSuccessResponse.class);
        } catch (ResourceAccessException e) {
            String message = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage())
                    .toLowerCase(Locale.ROOT);
            if (message.contains("timed out") || message.contains("timeout")) {
                throw new BusinessRuleException("Face-auth service timed out - please try again.");
            }
            throw new BusinessRuleException("Face-auth service is unreachable - please try again shortly.");
        } catch (RestClientResponseException e) {
            throw new BusinessRuleException(describeFailure(e));
        }

        if (response == null || response.embedding() == null || response.quality() == null || response.liveness() == null) {
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

    /**
     * Parses the real service's error body ({@code {"detail": {"error": "<code>", ...}}}, or
     * defensively a bare string {@code detail}) into a clean message. Falls back to a generic
     * status-text message if the body is missing, blank, or not parseable as JSON at all - a non-2xx
     * could come from a reverse proxy or load balancer rather than the service itself, in which case
     * the body may not be JSON.
     */
    private String describeFailure(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Face-auth service rejected the request: " + e.getStatusText();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detail = root.get("detail");
            if (detail == null || detail.isNull()) {
                return "Face-auth service rejected the request: " + e.getStatusText();
            }
            if (detail.isTextual()) {
                return detail.asText();
            }
            JsonNode error = detail.get("error");
            if (error == null || !error.isTextual()) {
                return "Face-auth service rejected the request: " + e.getStatusText();
            }
            // Carries through WHATEVER extra fields this error code happens to have (confirmed live
            // 2026-11-17 against the real deployment: invalid_image has "reason", multiple_faces_detected
            // has "count", image_too_large has "max_bytes") rather than hardcoding just one of them.
            List<String> extras = new ArrayList<>();
            Iterator<String> fields = detail.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!field.equals("error")) {
                    extras.add(field + "=" + detail.get(field).asText());
                }
            }
            return extras.isEmpty() ? error.asText() : error.asText() + " (" + String.join(", ", extras) + ")";
        } catch (Exception parseFailure) {
            return "Face-auth service rejected the request: " + e.getStatusText();
        }
    }

    private record EmbedSuccessResponse(
            List<Float> embedding, QualityWire quality, LivenessWire liveness
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
