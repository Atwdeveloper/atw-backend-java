package in.anytimework.atwbackend.kyc.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * UIDAI-verified identity details, pre-filled into the create-worker form after a successful Aadhaar
 * e-KYC OTP verification. {@code gender} is passed through as provider free text (not
 * {@code worker.domain.Gender}) - {@code kyc} cannot depend on another module's internal domain type,
 * and the admin UI already needs its own mapping to display/select the matching option regardless.
 *
 * <p>{@code stateId}/{@code cityId} are resolved server-side by case-insensitive exact match against
 * masterdata (see {@code WorkerIntakeService}); either is null when no confident match was found -
 * never an error - so {@code stateName}/{@code districtName} are also included verbatim for the admin
 * to pick the closest option manually when a resolution misses.</p>
 */
public record AadhaarVerificationResultDto(
        @Schema(description = "Id of the completed verification. Pass this as aadhaarVerificationId when "
                + "creating the worker (POST /api/v1/admin/workers) to auto-apply this verified identity and mark "
                + "KYC VERIFIED. Single-use - a second worker-creation attempt with the same id is rejected.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID verificationId,

        @Schema(description = "UIDAI-verified full name, exactly as returned by the provider.", example = "Ramesh Kumar")
        String fullName,

        @Schema(description = "Provider free text, NOT a validated/fixed enum at this layer (e.g. \"MALE\", "
                + "\"FEMALE\", \"TRANSGENDER\" from UIDAI) - map/display it client-side as needed.",
                example = "MALE")
        String gender,

        @Schema(description = "UIDAI-verified date of birth. Verification fails with a 422 if this implies an age under 18.",
                example = "1995-01-01")
        LocalDate dateOfBirth,

        @Schema(description = "UIDAI-verified address, line 1.", example = "123 MG Road")
        String addressLine1,

        @Schema(description = "UIDAI-verified address, line 2. Nullable - the provider does not always return a second line.",
                example = "null", nullable = true)
        String addressLine2,

        @Schema(description = "UIDAI-verified postal (PIN) code.", example = "110001")
        String pincode,

        @Schema(description = "UIDAI-verified state name, exactly as the provider returned it (free text, not necessarily masterdata's own spelling).",
                example = "Delhi")
        String stateName,

        @Schema(description = "ATW masterdata state id resolved by case-insensitive exact-name match against "
                + "stateName. Null (never an error) when no confident match was found - the admin then picks one "
                + "manually from GET /api/v1/masterdata/states.",
                example = "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2", nullable = true)
        UUID stateId,

        @Schema(description = "UIDAI-verified district name, exactly as the provider returned it - ATW's own "
                + "product vocabulary calls this \"city\".",
                example = "New Delhi")
        String districtName,

        @Schema(description = "ATW masterdata city (district) id resolved the same way as stateId - "
                + "case-insensitive exact match, null (never an error) when unresolved.",
                example = "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01", nullable = true)
        UUID cityId,

        @Schema(description = "Whether the provider returned a photo that was successfully stored as a document. "
                + "The document's id itself is not exposed by this endpoint.",
                example = "true")
        boolean hasPhoto
) {
}
