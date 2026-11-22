package in.anytimework.atwbackend.company.application;

import java.util.UUID;

/**
 * Name/industry only - deliberately excludes GST/PAN (their own verification lifecycle, see
 * {@code Company.markGstVerified}) and status (its own guarded transition, see
 * {@code CompanyService.updateStatus}). Also excludes addresses: mirrors
 * {@code worker.application.UpdateWorkerRequest}'s own precedent of leaving address management to a
 * separate sub-resource/service - see {@code CompanyService}'s class Javadoc for the one open
 * question this round intentionally leaves for whoever builds the admin controller.
 */
public record UpdateCompanyCommand(String name, UUID industryId) {
}
