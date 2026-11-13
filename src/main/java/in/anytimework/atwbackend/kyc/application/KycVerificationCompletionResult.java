package in.anytimework.atwbackend.kyc.application;

import in.anytimework.atwbackend.kyc.application.dto.KycProfileDto;

import java.util.UUID;

/**
 * Result of completing a worker's KYC from a successful Aadhaar e-KYC verification. Besides the
 * resulting profile, hands back the verification's captured photo document id (if the provider
 * returned one) so {@code WorkerService.create()} can apply it as the new worker's profile photo
 * without this module needing to know anything about worker profile photos itself.
 */
public record KycVerificationCompletionResult(KycProfileDto profile, UUID profilePhotoDocumentId) {
}
