package in.anytimework.atwbackend.kyc.api.admin;

import in.anytimework.atwbackend.kyc.api.admin.dto.SendAadhaarOtpRequest;
import in.anytimework.atwbackend.kyc.api.admin.dto.SendMobileOtpRequest;
import in.anytimework.atwbackend.kyc.api.admin.dto.VerifyAadhaarOtpRequest;
import in.anytimework.atwbackend.kyc.api.admin.dto.VerifyMobileOtpRequest;
import in.anytimework.atwbackend.kyc.application.WorkerIntakeService;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarOtpChallengeDto;
import in.anytimework.atwbackend.kyc.application.dto.AadhaarVerificationResultDto;
import in.anytimework.atwbackend.kyc.application.dto.MobileOtpChallengeDto;
import in.anytimework.atwbackend.shared.api.ApiError;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.security.HttpRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "add worker via Aadhaar" intake wizard: verify a mobile number by OTP, then verify Aadhaar via
 * e-KYC OTP to pull UIDAI-sourced identity details straight into the create-worker form (see
 * {@code CreateWorkerRequest.aadhaarVerificationId}). Gated by {@code workers.create} - the same
 * permission as manual worker creation - rather than a {@code kyc.*} permission, since this is simply
 * the other on-ramp to the same outcome, not a KYC-review action on an existing worker.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/workers/intake")
@Tag(name = "Admin - Worker Intake (Aadhaar e-KYC)",
        description = "The \"add worker via Aadhaar\" wizard: verify a mobile number by OTP, then verify "
                + "Aadhaar via a live e-KYC OTP call to pull UIDAI-sourced identity details straight into the "
                + "create-worker form. Gated by workers.create (the same permission as manual worker creation) "
                + "rather than a kyc.* permission - this is simply the other on-ramp to creating a worker, not a "
                + "KYC-review action on an existing one.")
public class AdminWorkerIntakeController {

    private final WorkerIntakeService intakeService;

    public AdminWorkerIntakeController(WorkerIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/mobile-otp")
    @PreAuthorize("@authz.has('workers.create')")
    @Operation(summary = "Send a mobile-number OTP",
            description = "First step of the intake wizard - sends a 6-digit SMS OTP to prove the admin has "
                    + "access to the worker's mobile number before proceeding to Aadhaar verification. "
                    + "Rate limited to 3 sends per mobile number and 10 sends per client IP, both within a "
                    + "rolling 10-minute window; either limit hit returns the same error. The code expires in 10 "
                    + "minutes and is never echoed back in this response. In local/dev (default "
                    + "atw.sms.provider=log), it is only written to the application log, not actually sent by SMS.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "OTP generated and sent (or logged, in local dev).",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MobileOtpChallengeDto.class),
                            examples = @ExampleObject(name = "OTP sent", value = """
                                    {
                                      "success": true,
                                      "data": { "otpId": "9c3d1b6a-0e5f-4b7c-9a2d-1f6e8c4d5b03", "expiresInSeconds": 600 },
                                      "error": null
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Rate limit exceeded (per mobile number or per client IP), or the SMS provider "
                            + "itself failed to send.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Rate limited", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "BUSINESS_RULE_VIOLATION",
                                        "message": "Too many attempts. Please try again later.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:00:00Z",
                                        "traceId": "a1b2c3d4e5f60718"
                                      }
                                    }""")))
    })
    public ApiResponse<MobileOtpChallengeDto> sendMobileOtp(@Valid @RequestBody SendMobileOtpRequest request, HttpServletRequest http) {
        return ApiResponse.ok(intakeService.sendMobileOtp(request.mobileNumber(), HttpRequests.clientIp(http)));
    }

    @PostMapping("/mobile-otp/verify")
    @PreAuthorize("@authz.has('workers.create')")
    @Operation(summary = "Verify a mobile-number OTP",
            description = "Consumes the OTP from Send Mobile OTP. Max 5 attempts per OTP (counted even on a "
                    + "wrong guess) before it is locked out and a new one must be requested. Returns no payload - "
                    + "success is indicated by the envelope's success:true alone. Safe to retry on a network "
                    + "timeout: a concurrent duplicate submit of the same OTP id is retried internally (up to 5 "
                    + "times) against the latest row rather than racing into a spurious error.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "OTP verified and consumed.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(description = "No payload - success is indicated by the envelope's success:true alone."),
                            examples = @ExampleObject(name = "Verified", value = """
                                    { "success": true, "data": null, "error": null }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Unknown, already-consumed, or expired otpId, or a wrong code - deliberately the "
                            + "same message for all three so a caller can't probe which case occurred.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Invalid code", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "VALIDATION_ERROR",
                                        "message": "Invalid or expired verification code",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:01:00Z",
                                        "traceId": "b2c3d4e5f6071829"
                                      }
                                    }""")))
    })
    public ApiResponse<Void> verifyMobileOtp(@Valid @RequestBody VerifyMobileOtpRequest request) {
        intakeService.verifyMobileOtp(request.otpId(), request.code());
        return ApiResponse.ok();
    }

    @PostMapping("/aadhaar-otp")
    @PreAuthorize("@authz.has('workers.create')")
    @Operation(summary = "Send an Aadhaar e-KYC OTP",
            description = "Second step of the intake wizard - starts a live Aadhaar e-KYC OTP verification. "
                    + "Checked in order: (1) consentGiven must be true, (2) this Aadhaar number must not already "
                    + "belong to another worker's KYC profile, (3) rate limits (3 sends per Aadhaar number, 10 per "
                    + "client IP, both per rolling 10 minutes - the number is HMAC-hashed before being used as the "
                    + "rate-limit/duplicate-check key), (4) the configured e-KYC provider call itself. Provider is "
                    + "swappable via atw.aadhaar-kyc.provider (default stub): the bundled local stub never rejects "
                    + "this call and returns a synthetic reference id, logging what it did instead of calling a "
                    + "real government-backed API. The real sandbox provider (Sandbox.co.in, a live, "
                    + "typically-paid UIDAI e-KYC vendor) can reject the request outright (unrecognized Aadhaar "
                    + "number, cooldown, provider outage).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "OTP sent by the configured e-KYC provider.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AadhaarOtpChallengeDto.class),
                            examples = @ExampleObject(name = "OTP sent", value = """
                                    {
                                      "success": true,
                                      "data": { "verificationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "expiresInSeconds": 600 },
                                      "error": null
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "This Aadhaar number is already registered (verified or just recorded) to a "
                            + "different worker.",
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
                                        "timestamp": "2026-11-13T10:02:00Z",
                                        "traceId": "c3d4e5f607182930"
                                      }
                                    }""")))
    })
    public ApiResponse<AadhaarOtpChallengeDto> sendAadhaarOtp(@Valid @RequestBody SendAadhaarOtpRequest request, HttpServletRequest http) {
        return ApiResponse.ok(intakeService.sendAadhaarOtp(request.aadhaarNumber(), request.consentGiven(), HttpRequests.clientIp(http)));
    }

    @PostMapping("/aadhaar-otp/verify")
    @PreAuthorize("@authz.has('workers.create')")
    @Operation(summary = "Verify an Aadhaar e-KYC OTP",
            description = "Completes the e-KYC verification and returns the UIDAI-verified identity. Checked in "
                    + "order: the verification exists and is not already consumed/verified, is not expired "
                    + "(10-minute TTL), has fewer than 3 prior attempts, then the provider call itself, then a "
                    + "minimum-age check (worker must be at least 18, derived from the verified date of birth - "
                    + "underage fails permanently; a fresh Send Aadhaar OTP is required to try again). On success: "
                    + "stores the provider's photo (if any) as a document, and resolves stateName/districtName "
                    + "against masterdata geography by case-insensitive exact name match - stateId/cityId come "
                    + "back null (never an error) when unresolved, and the admin picks the closest option "
                    + "manually in that case. gender is passed through exactly as the provider returned it (free "
                    + "text - not a validated enum at this layer). The returned verificationId is the value to "
                    + "pass as aadhaarVerificationId when creating the worker (POST /api/v1/admin/workers) - that "
                    + "call consumes it to mark the new worker's KYC VERIFIED and carry over the verified photo. "
                    + "Single-use: a second attempt to reuse it fails worker creation.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Aadhaar e-KYC succeeded; verified identity returned.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AadhaarVerificationResultDto.class),
                            examples = @ExampleObject(name = "Verified (local stub)", value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "verificationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                        "fullName": "Stub Verified Worker",
                                        "gender": "MALE",
                                        "dateOfBirth": "1995-01-01",
                                        "addressLine1": "123 Stub Street",
                                        "addressLine2": null,
                                        "pincode": "110001",
                                        "stateName": "Delhi",
                                        "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2",
                                        "districtName": "New Delhi",
                                        "cityId": "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01",
                                        "hasPhoto": false
                                      },
                                      "error": null
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
                    description = "Too many wrong attempts, a provider-rejected/expired OTP, or the verified "
                            + "identity implies an age under 18.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(name = "Underage", value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "error": {
                                        "code": "BUSINESS_RULE_VIOLATION",
                                        "message": "Worker must be at least 18 years old.",
                                        "fieldErrors": null,
                                        "timestamp": "2026-11-13T10:03:00Z",
                                        "traceId": "d4e5f6071829304b"
                                      }
                                    }""")))
    })
    public ApiResponse<AadhaarVerificationResultDto> verifyAadhaarOtp(@Valid @RequestBody VerifyAadhaarOtpRequest request) {
        return ApiResponse.ok(intakeService.verifyAadhaarOtp(request.verificationId(), request.otp()));
    }
}
