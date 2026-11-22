/**
 * Public, read-only API of the company module (Spring Modulith named interface "api"): other modules
 * depend on {@link in.anytimework.atwbackend.company.application.lookup.CompanyLookupService} here,
 * never on the full {@code company.application.CompanyService} or anything in {@code company.domain},
 * which stay internal. Splitting this into its own sub-package (rather than marking the whole
 * {@code company.application} package as the named interface, as {@code notification}/{@code document}
 * do) is deliberate: {@code CompanyService} must stay public (the admin controller in a sibling
 * package calls it), so a package-level annotation covering both would expose it to other modules
 * just as readily as the interface meant to be the only sanctioned entry point - exactly mirroring
 * why {@code masterdata.application.lookup} is its own sub-package rather than annotating the whole
 * {@code masterdata.application} package.
 *
 * <p>Nothing currently depends on this - company is a brand new module with no cross-module callers
 * yet - but declaring the boundary now, before one exists, is what already protects
 * {@code kyc}/{@code faceauth}/{@code worker}/{@code document}/{@code notification}/
 * {@code masterdata.application.lookup}; without it, a future module (e.g. {@code vacancy},
 * {@code attendance}, {@code billing}) could silently reach straight into
 * {@code company.domain.CompanyRepository} with no build-time warning.</p>
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.company.application.lookup;
