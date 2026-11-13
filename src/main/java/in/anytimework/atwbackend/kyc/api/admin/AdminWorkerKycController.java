package in.anytimework.atwbackend.kyc.api.admin;

import in.anytimework.atwbackend.kyc.api.admin.dto.SubmitAadhaarRequest;
import in.anytimework.atwbackend.kyc.api.admin.dto.SubmitPanRequest;
import in.anytimework.atwbackend.kyc.application.KycService;
import in.anytimework.atwbackend.kyc.application.dto.KycProfileDto;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Worker KYC: view a worker's KYC profile, and manually record identity numbers. Manual document
 * upload/review was removed - a live Aadhaar e-KYC verification (see
 * {@code AdminWorkerIntakeController}, consumed via {@code WorkerService.create()}) is itself the
 * proof of identity, so there is nothing left for an admin to manually review. These two endpoints
 * remain only for the manual fallback path - recording a declared number as-is, unverified - for a
 * worker who didn't go through (or whose) Aadhaar e-KYC didn't succeed.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin")
@Tag(name = "Admin - Worker KYC",
        description = "View a worker's KYC profile, and manually record identity numbers as an unverified "
                + "fallback. Manual document upload/review was removed from this module - a live Aadhaar e-KYC "
                + "verification (see the Admin - Worker Intake (Aadhaar e-KYC) tag) is itself the proof of "
                + "identity, so there is nothing left here for an admin to manually review; these endpoints exist "
                + "only for a worker whose Aadhaar e-KYC didn't happen or didn't succeed.")
public class AdminWorkerKycController {

    private final KycService kycService;

    public AdminWorkerKycController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping("/workers/{workerId}/kyc")
    @PreAuthorize("@authz.has('kyc.view')")
    @Operation(summary = "Get a worker's KYC profile",
            description = "Never 404s, even for an unknown workerId - a worker with no KYC profile row yet "
                    + "returns a NOT_STARTED placeholder (overallStatus: \"NOT_STARTED\", both numbers null, "
                    + "verifiedAt/verificationRef both null). This is a deliberate \"always 200\" design so a "
                    + "worker-detail screen never needs special-case error handling just to render its KYC tab. "
                    + "aadhaarNumber/panNumber are masked to the last 4 characters unless the caller also holds "
                    + "kyc.view_sensitive, in which case they come back unmasked.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "KYC profile (or a NOT_STARTED placeholder if none exists yet for this worker).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = KycProfileDto.class),
                            examples = {
                                    @ExampleObject(name = "Unverified, masked", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "workerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                                "aadhaarNumber": "********2346",
                                                "panNumber": null,
                                                "overallStatus": "PENDING",
                                                "verifiedAt": null,
                                                "verificationRef": null
                                              },
                                              "error": null
                                            }"""),
                                    @ExampleObject(name = "No KYC submitted yet (placeholder, not a 404)", value = """
                                            {
                                              "success": true,
                                              "data": {
                                                "workerId": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c",
                                                "aadhaarNumber": null,
                                                "panNumber": null,
                                                "overallStatus": "NOT_STARTED",
                                                "verifiedAt": null,
                                                "verificationRef": null
                                              },
                                              "error": null
                                            }""")
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "workerId path segment is not a valid UUID. Note this is the only failure this "
                            + "endpoint actually produces - an unknown-but-well-formed workerId still returns 200 "
                            + "with the NOT_STARTED placeholder above, not a 404.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Malformed id", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid value for parameter 'workerId'",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:04:00Z",
                                        "traceId": "e5f607182930415c"
                                      }
                                    }""")))
    })
    public ApiResponse<KycProfileDto> getProfile(
            @Parameter(description = "The worker to look up.", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID workerId) {
        return ApiResponse.ok(kycService.getProfile(workerId));
    }

    @PutMapping("/workers/{workerId}/kyc/aadhaar")
    @PreAuthorize("@authz.has('kyc.submit')")
    @Operation(summary = "Manually submit a worker's Aadhaar number",
            description = "Manual, unverified fallback for a worker who didn't go through (or whose) Aadhaar "
                    + "e-KYC didn't succeed - see the Admin - Worker Intake (Aadhaar e-KYC) tag for the live "
                    + "verification path. Moves overallStatus from NOT_STARTED to PENDING; leaves it unchanged if "
                    + "already further along (PENDING/VERIFIED/REJECTED), so this can never downgrade an "
                    + "already-verified profile. Creates the worker's KYC profile row on first use if one "
                    + "doesn't exist yet. workerId is not checked against the worker module before this write - "
                    + "a nonexistent workerId is expected to be rejected only by the database's own foreign-key "
                    + "constraint on worker_kyc_profile.worker_id (surfaced generically as 409 CONFLICT), not a "
                    + "clean 404. Always use a real workerId from a prior Create Worker call.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Aadhaar number recorded.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = KycProfileDto.class),
                            examples = @ExampleObject(name = "Recorded, now PENDING", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "workerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                        "aadhaarNumber": "********2346",
                                        "panNumber": null,
                                        "overallStatus": "PENDING",
                                        "verifiedAt": null,
                                        "verificationRef": null
                                      },
                                      "error": null
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "This Aadhaar number already belongs to a different worker's KYC profile (or, "
                            + "rarely, workerId itself does not correspond to a real worker row - see this "
                            + "endpoint's description).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Duplicate Aadhaar", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "CONFLICT",
                                        "message": "This Aadhaar number is already registered to another worker.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:05:00Z",
                                        "traceId": "f607182930415c6d"
                                      }
                                    }""")))
    })
    public ApiResponse<KycProfileDto> submitAadhaar(
            @Parameter(description = "The worker to record this Aadhaar number against.", required = true,
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID workerId,
            @Valid @RequestBody SubmitAadhaarRequest request) {
        return ApiResponse.ok(kycService.submitAadhaar(workerId, request.aadhaarNumber()));
    }

    @PutMapping("/workers/{workerId}/kyc/pan")
    @PreAuthorize("@authz.has('kyc.submit')")
    @Operation(summary = "Manually submit a worker's PAN",
            description = "Same manual, unverified-fallback shape as Submit Aadhaar, for PAN. As of 2026-11-13, "
                    + "panNumber must match 5 letters, 4 digits, 1 letter (e.g. ABCPE1234F) - this format check is "
                    + "new; case-insensitive input is accepted and normalized to uppercase before the pattern "
                    + "runs. Same duplicate-across-workers check and same NOT_STARTED -> PENDING transition as "
                    + "Submit Aadhaar (that duplicate check is also new as of 2026-11 - previously two workers "
                    + "could be given the same PAN with no error at all). workerId has the same no-existence-check "
                    + "behavior described on Submit Aadhaar.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "PAN recorded.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = KycProfileDto.class),
                            examples = @ExampleObject(name = "Recorded, now PENDING", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "workerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                        "aadhaarNumber": null,
                                        "panNumber": "******234F",
                                        "overallStatus": "PENDING",
                                        "verifiedAt": null,
                                        "verificationRef": null
                                      },
                                      "error": null
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "panNumber doesn't match the required format.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Bad PAN format", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Request validation failed",
                                        "fieldErrors": [
                                          { "field": "panNumber", "message": "PAN must be 5 letters, 4 digits, 1 letter (e.g. ABCPE1234F)" }
                                        ],
                                        "timestamp": "2026-11-13T10:06:00Z",
                                        "traceId": "071829304115c6d7"
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "This PAN already belongs to a different worker's KYC profile.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Duplicate PAN", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "CONFLICT",
                                        "message": "This PAN is already registered to another worker.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:07:00Z",
                                        "traceId": "182930415c6d7e08"
                                      }
                                    }""")))
    })
    public ApiResponse<KycProfileDto> submitPan(
            @Parameter(description = "The worker to record this PAN against.", required = true,
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID workerId,
            @Valid @RequestBody SubmitPanRequest request) {
        return ApiResponse.ok(kycService.submitPan(workerId, request.panNumber()));
    }
}
