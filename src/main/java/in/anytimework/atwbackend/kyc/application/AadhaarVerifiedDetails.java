package in.anytimework.atwbackend.kyc.application;

import java.time.LocalDate;

/**
 * UIDAI-verified identity details as returned by an Aadhaar e-KYC provider, raw/unmapped - gender
 * and address are provider free text (not yet normalized to this app's {@code Gender} enum or
 * masterdata state/city ids); that mapping happens in {@code WorkerIntakeServiceImpl}, which knows
 * about those concepts so this application-layer record (part of the public contract an adapter
 * implements) doesn't need to depend on the worker/masterdata modules.
 */
public record AadhaarVerifiedDetails(
        String fullName,
        String gender,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String pincode,
        String stateName,
        String districtName,
        byte[] photo
) {
}
