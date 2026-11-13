package in.anytimework.atwbackend.faceauth.api.admin;

import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Worker face-template enrollment - the admin-triggered side only (self-service worker-portal
 * enrollment, and company-side attendance verification, are legacy capabilities deliberately not
 * carried over yet - see project memory). {@code faceauth.*} permissions are separate from
 * {@code workers.*}/{@code kyc.*}, matching the existing precedent of a narrower role being grantable
 * without general worker-management rights. Nested under {@code /workers/{workerId}/face-enrollment}
 * for API discoverability, even though it's a different module's controller - same precedent as
 * {@code AdminWorkerKycController}.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin")
@Tag(name = "Worker - Face Enrollment", description = "Admin-triggered worker face-template enrollment for a "
        + "future attendance face-match feature - the admin side only; self-service worker-portal enrollment "
        + "and company-side attendance verification are legacy capabilities deliberately not carried over "
        + "yet. A dedicated `faceauth` module, not part of `kyc`: whether a worker has a face template on "
        + "file is an independent fact, with its own `faceauth.*` permissions separate from "
        + "`workers.*`/`kyc.*`. Nested under `/admin/workers/{workerId}/face-enrollment` for API "
        + "discoverability even though it's a different module's controller (same precedent as Worker KYC). "
        + "The Worker module's own `GET /workers/{id}` already embeds this exact shape as its "
        + "`faceEnrollment` field, and its list endpoint carries a lighter `faceEnrolledAt` timestamp - the "
        + "endpoints here are for actually enrolling, re-enrolling, removing, or polling status on their own.")
public class AdminWorkerFaceEnrollmentController {

    private final FaceAuthService faceAuthService;

    public AdminWorkerFaceEnrollmentController(FaceAuthService faceAuthService) {
        this.faceAuthService = faceAuthService;
    }

    @GetMapping("/workers/{workerId}/face-enrollment")
    @PreAuthorize("@authz.has('faceauth.view')")
    @Operation(summary = "Get a worker's face-enrollment status", description = """
            Permission: `faceauth.view`.

            Never returns 404, even for a `workerId` that doesn't exist or has simply never enrolled - a \
            not-enrolled placeholder (`enrolled: false`, every other field `null`) comes back instead, so a \
            caller never needs a separate existence check before rendering an enrollment panel. This read \
            performs no check against the worker table itself either way (confirmed against \
            `FaceAuthServiceImpl.getEnrollment`) - don't rely on it to validate that a `workerId` is real.

            There is no separate "is this worker enrolled" endpoint - `data.enrolled` here is the only way \
            to ask that through this API. `photoUrl` is a short-lived (about 15 minutes) signed Azure Blob \
            URL; call this endpoint again for a fresh one rather than caching it past expiry.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enrollment status (enrolled or not - see the two examples)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FaceEnrollmentDto.class), examples = {
                            @ExampleObject(name = "enrolled", value = """
                                    { "success": true, "data": { "workerId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "enrolled": true, "enrolledAt": "2026-11-10T09:15:00Z", "livenessConfidence": 0.97, "photoUrl": "https://atwstorage.blob.core.windows.net/documents/worker_face_enrollment_photo/3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b/7f3e2d1c-0b9a-4f8e-9d7c-6b5a4f3e2d1c.jpg?sv=2025-05-05&se=2026-11-10T09%3A30%3A00Z&sr=b&sp=r&sig=REDACTED" }, "error": null }"""),
                            @ExampleObject(name = "not-enrolled", value = """
                                    { "success": true, "data": { "workerId": "99999999-9999-4999-8999-999999999999", "enrolled": false, "enrolledAt": null, "livenessConfidence": null, "photoUrl": null }, "error": null }""")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`workerId` path segment is not a valid UUID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Invalid value for parameter 'workerId'", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<FaceEnrollmentDto> getEnrollment(
            @Parameter(description = "Worker id.", required = true) @PathVariable UUID workerId) {
        return ApiResponse.ok(faceAuthService.getEnrollment(workerId));
    }

    @PutMapping(value = "/workers/{workerId}/face-enrollment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('faceauth.enroll')")
    @Operation(summary = "Enroll or re-enroll a worker's face template", description = """
            Permission: `faceauth.enroll`.

            Multipart form-data with two parts: `file` (the reference photo) and `consentGiven`. The \
            caller's IP is recorded server-side as the consent IP (from `X-Forwarded-For`'s last trusted \
            hop, else the socket address) - there is no request field for it.

            Re-enrolling a worker who already has a template fully **overwrites** it; there is no history \
            of past enrollments, only ever the current one. The previous enrollment photo is deleted once \
            the new one is stored.

            Validation, checked in this order (all -> 400 VALIDATION_ERROR, nothing persisted):

            1. `consentGiven` must be `true` - enrollment can never proceed without this worker's explicit, \
            recorded consent.
            2. `file` must be non-empty and at most 2MB.
            3. Its declared `Content-Type` must be exactly `image/png`, `image/jpeg`, or `image/webp`.
            4. Its actual leading bytes must match that declared type (magic-byte check - the same guard \
            used on the Masterdata icon uploads).

            Business rules, checked by the face-auth provider itself once validation passes (all -> 422 \
            BUSINESS_RULE_VIOLATION, nothing persisted - ATW never stores a template the provider itself \
            considers unreliable):

            1. Image quality must be acceptable - rejection reasons (e.g. too blurry, face too small, poor \
            lighting) come verbatim from the provider.
            2. The liveness check must pass - rejects an obvious photo-of-a-photo/spoof attempt.

            Locally and in tests (`atw.face-auth.provider` unset, defaulting to the stub adapter), both \
            business rules above always pass for any input - they can only actually fail once \
            `atw.face-auth.provider=python` points at a real deployment of the separate face-auth \
            service.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enrolled (or re-enrolled, overwriting the previous template)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FaceEnrollmentDto.class), examples = @ExampleObject(value = """
                            { "success": true, "data": { "workerId": "3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b", "enrolled": true, "enrolledAt": "2026-11-13T06:40:00Z", "livenessConfidence": 0.97, "photoUrl": "https://atwstorage.blob.core.windows.net/documents/worker_face_enrollment_photo/3e2d1c0b-9a8f-4e7d-8c6b-5a4f3e2d1c0b/7f3e2d1c-0b9a-4f8e-9d7c-6b5a4f3e2d1c.jpg?sv=2025-05-05&se=2026-11-13T06%3A55%3A00Z&sr=b&sp=r&sig=REDACTED" }, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Consent not given, or the uploaded file itself fails validation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                            @ExampleObject(name = "consent-not-given", value = """
                                    { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Worker consent is required before enrolling a face template.", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }"""),
                            @ExampleObject(name = "unsupported-content-type", value = """
                                    { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Unsupported image content type: application/pdf (allowed: image/png, image/jpeg, image/webp)", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }""")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "The face-auth provider rejected the photo itself - nothing was persisted",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {
                            @ExampleObject(name = "quality-insufficient", value = """
                                    { "success": false, "data": null, "error": { "code": "BUSINESS_RULE_VIOLATION", "message": "Image quality is insufficient for enrollment: face too small, image too blurry", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }"""),
                            @ExampleObject(name = "liveness-failed", value = """
                                    { "success": false, "data": null, "error": { "code": "BUSINESS_RULE_VIOLATION", "message": "Liveness check failed - please retake the photo of the worker in person.", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }""")
                    }))
    })
    public ApiResponse<FaceEnrollmentDto> enroll(
            @Parameter(description = "Worker id.", required = true) @PathVariable UUID workerId,
            @Parameter(description = "Reference photo. PNG, JPEG, or WEBP; at most 2MB; must pass the "
                    + "provider's own quality and liveness checks (see this operation's description).", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Explicit worker consent to enroll their face template. Must be "
                    + "`true` - `false` (or omitted) is rejected before the file is even inspected.",
                    required = true, example = "true")
            @RequestParam boolean consentGiven,
            HttpServletRequest http) {
        return ApiResponse.ok(faceAuthService.enroll(workerId, file.getOriginalFilename(), file.getContentType(),
                readBytes(file), consentGiven, HttpRequests.clientIp(http)));
    }

    @DeleteMapping("/workers/{workerId}/face-enrollment")
    @PreAuthorize("@authz.has('faceauth.enroll')")
    @Operation(summary = "Remove a worker's face enrollment", description = """
            Permission: `faceauth.enroll` - the same permission as enroll/re-enroll; there is no separate \
            `faceauth.delete`.

            Deletes the stored template and its reference photo. **Not an error if nothing was enrolled** \
            - this is a "make sure nothing is enrolled" operation, not a "delete this specific enrollment" \
            one (same convention as Masterdata's Remove Industry Image).""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Removed (or there was nothing to remove either way)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`workerId` path segment is not a valid UUID",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = """
                            { "success": false, "data": null, "error": { "code": "VALIDATION_ERROR", "message": "Invalid value for parameter 'workerId'", "fieldErrors": null, "timestamp": "2026-11-13T06:40:00Z", "traceId": "a1b2c3d4e5f60718" } }""")))
    })
    public ApiResponse<Void> removeEnrollment(
            @Parameter(description = "Worker id.", required = true) @PathVariable UUID workerId) {
        faceAuthService.removeEnrollment(workerId);
        return ApiResponse.ok();
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
