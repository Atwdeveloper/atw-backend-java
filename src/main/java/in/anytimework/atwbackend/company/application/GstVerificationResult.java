package in.anytimework.atwbackend.company.application;

/**
 * Raw outcome of a GST-number verification call - deliberately minimal (the fields
 * {@code CompanyService.triggerGstVerification} actually needs: persist a verification reference +
 * timestamp on success, or surface a clean reason on failure), not the full company-profile payload
 * Sandbox.co.in's GSTIN-search response can return (trade name, business nature/activities,
 * taxpayer type, full address breakdown, jurisdiction codes, ...). Nothing in this round's
 * {@code company} table or API surface stores/returns that richer data - a future, separate
 * "GST lookup/prefill" feature (mirroring the legacy admin app's own standalone prefill endpoint)
 * can extend this once a real caller needs it, without having to re-derive the wire format.
 *
 * <p>Mirrors {@code worker.application.BankVerificationResult}'s contract shape: a provider
 * unreachable/rejecting the request outright is a thrown {@code BusinessRuleException} from
 * {@link GstVerificationPort#verify}, never this result - {@code verified=false} is reserved for a
 * normal, in-domain negative fact the provider successfully reported (invalid GSTIN pattern, GSTIN
 * not found, GSTIN not active), exactly how {@code BankVerificationResult.exists=false} is "a normal
 * result", not an error.</p>
 *
 * @param verified whether the provider confirmed this GSTIN is a real, active registration
 * @param legalName the registered legal name on file for this GSTIN (Sandbox's {@code lgnm}) - null if not verified
 * @param state the state name on file for this GSTIN (Sandbox's {@code addr.stcd} - despite the name,
 *              a full state name, not a numeric code; see {@code SandboxGstVerificationAdapter}'s Javadoc) - null if not verified
 * @param providerReference an identifier for this specific verification call, kept for audit - not secret
 * @param failureReason a human-readable reason {@code verified} is false - null when {@code verified} is true
 */
public record GstVerificationResult(boolean verified, String legalName, String state, String providerReference,
                                     String failureReason) {
}
