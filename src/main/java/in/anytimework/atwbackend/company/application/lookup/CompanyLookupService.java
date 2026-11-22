package in.anytimework.atwbackend.company.application.lookup;

import java.util.UUID;

/**
 * Minimal, read-only existence/summary lookup for other modules (vacancy/attendance/billing/... in
 * the future) to depend on, mirroring {@code masterdata.application.lookup}'s *LookupService split -
 * this package, not the full {@code company.application.CompanyService}, is the one sanctioned
 * cross-module entry point into this module (see this package's {@code package-info.java}).
 *
 * <p>Deliberately has no {@code list()} - unlike masterdata's small, dropdown-sized reference lists
 * (industries, designations), the number of companies is expected to grow without bound, so
 * "load every company" is never a safe operation to expose here.</p>
 */
public interface CompanyLookupService {

    CompanySummary get(UUID id);

    boolean exists(UUID id);
}
