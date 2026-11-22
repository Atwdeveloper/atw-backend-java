package in.anytimework.atwbackend.company.application.lookup;

import java.util.UUID;

/**
 * Minimal, cross-module-safe view of a company - deliberately excludes {@code CompanyStatus} (a
 * {@code company.domain} type that must not leak across the module boundary through this interface)
 * and anything sensitive (GST/PAN, owner details). Mirrors
 * {@code masterdata.application.lookup.IndustrySummary}'s own minimal shape (id/name/code), which
 * likewise excludes its entity's status enum - {@link CompanyLookupService#exists} answers "does
 * this id exist at all", same as {@code IndustryLookupService.exists}/{@code DesignationLookupService.exists},
 * not "is it currently active/ACTIVE".
 *
 * <p>A future consumer that genuinely needs to gate on company status (e.g. a vacancy module
 * checking "is this company still ACTIVE before accepting a new posting") should extend this
 * interface then, with a real caller to validate the shape against - not have it guessed at now.</p>
 */
public record CompanySummary(UUID id, String companyCode, String name, UUID industryId) {
}
