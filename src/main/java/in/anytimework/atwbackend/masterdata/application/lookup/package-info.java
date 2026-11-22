/**
 * Public, read-only API of the masterdata module (Spring Modulith named interface "api"): other
 * modules depend on the {@code *LookupService} interfaces here, never on the full-CRUD
 * {@code *Service} classes in the parent {@code application} package, which stay internal. Splitting
 * this into its own sub-package (rather than marking the whole {@code application} package as the
 * named interface, as {@code notification}/{@code document} do) is deliberate: those full-CRUD
 * classes must be public (the admin controllers in a different package call them), so a
 * package-level annotation covering both would expose them to other modules just as readily as the
 * interfaces meant to be the only sanctioned entry point.
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.masterdata.application.lookup;
