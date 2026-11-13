package in.anytimework.atwbackend.kyc.application.dto;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** aadhaarNumber/panNumber are masked (e.g. "********9012") unless the caller holds kyc.view_sensitive. */
public record KycProfileDto(
        @Schema(description = "The worker this KYC profile belongs to.", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID workerId,

        @Schema(description = "Aadhaar number, masked to the last 4 characters (e.g. \"********2346\") unless the "
                + "caller holds kyc.view_sensitive. Null if none has been recorded yet.",
                example = "********2346", nullable = true)
        String aadhaarNumber,

        @Schema(description = "PAN number, masked the same way as aadhaarNumber. Null if none has been recorded yet.",
                example = "******234F", nullable = true)
        String panNumber,

        @Schema(description = "NOT_STARTED (no KYC profile yet) | PENDING (manually recorded, unverified) | "
                + "VERIFIED (a successful live Aadhaar e-KYC call) | REJECTED (reserved for future use - nothing "
                + "in this module sets it today).",
                example = "VERIFIED")
        KycStatus overallStatus,

        @Schema(description = "When overallStatus became VERIFIED via a live Aadhaar e-KYC call. Null unless VERIFIED.",
                example = "2026-11-10T09:15:00Z", nullable = true)
        Instant verifiedAt,

        @Schema(description = "The e-KYC provider's reference id for the verification that produced VERIFIED - an "
                + "audit/dispute trail, not secret. Null unless VERIFIED via the live API.",
                example = "stub-3fa85f64-5717-4562-b3fc-2c963f66afa6", nullable = true)
        String verificationRef
) {
}
