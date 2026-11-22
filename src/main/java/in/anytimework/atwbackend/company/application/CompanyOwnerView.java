package in.anytimework.atwbackend.company.application;

import java.util.UUID;

/** Small nested reference to a company's owner, embedded in {@link CompanyView} - mirrors the locked *Ref naming convention (e.g. CompanyOwnerRef) the admin API surface will expose in part 2. */
public record CompanyOwnerView(UUID id, String name, String email) {
}
