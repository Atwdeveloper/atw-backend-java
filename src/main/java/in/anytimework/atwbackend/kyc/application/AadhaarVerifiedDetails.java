package in.anytimework.atwbackend.kyc.application;

import java.time.LocalDate;

/**
 * UIDAI-verified identity details as returned by an Aadhaar e-KYC provider, raw/unmapped - gender
 * and address are provider free text (not yet normalized to this app's {@code Gender} enum or
 * masterdata state/city ids); that mapping happens in {@code WorkerIntakeServiceImpl}, which knows
 * about those concepts so this application-layer record (part of the public contract an adapter
 * implements) doesn't need to depend on the worker/masterdata modules.
 *
 * <p>{@code fatherName} is the provider's "care of" value exactly as returned - UIDAI's own
 * convention there is often a prefixed string (e.g. {@code "S/O: Ramesh Kumar"}, or {@code "D/O"}/
 * {@code "W/O"}), not a clean name on its own. Deliberately NOT stripped/parsed here: there is no
 * single safe pattern covering every prefix variant, and silently mangling it risks losing/corrupting
 * real data. The admin reviews and can edit the pre-filled create-worker form before submitting, so a
 * raw value they can clean up themselves is safer than a confident-looking wrong guess. Null if the
 * provider doesn't return one. Added 2026-11-16 - earlier versions of this adapter deliberately did
 * not capture this field at all (a known, explicitly-flagged gap until now).</p>
 */
public record AadhaarVerifiedDetails(
        String fullName,
        String fatherName,
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
