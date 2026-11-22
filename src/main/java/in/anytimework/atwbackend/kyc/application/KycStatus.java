package in.anytimework.atwbackend.kyc.application;

/**
 * Aggregate verification state of a worker's KYC profile, computed from its documents' statuses.
 * Lives in {@code application} (the kyc module's public contract, part of the "api" named
 * interface), not {@code domain}: every {@link KycService} method already exposes this type, and
 * {@code worker} - the module that actually needs it - can only depend on kyc's named interface, not
 * its internal entities/enums. {@code kyc.domain.WorkerKycProfile} (the entity whose persisted state
 * this represents) imports it from here; that one intra-module reference from domain to application
 * is accepted as the pragmatic cost of this type genuinely being contract, not internal detail -
 * compare {@code document.domain.DocumentUploadStatus}, which stays in {@code domain} precisely
 * because it is purely internal and is deliberately never exposed through {@code DocumentDto}.
 */
public enum KycStatus {
    NOT_STARTED,
    PENDING,
    VERIFIED,
    REJECTED
}
