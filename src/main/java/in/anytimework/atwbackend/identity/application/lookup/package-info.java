/**
 * Public, read-only API of the identity module (Spring Modulith named interface "api"): other
 * modules resolving an admin id to a display name go through {@link
 * in.anytimework.atwbackend.identity.application.lookup.AdminActorLookupService} here, never the
 * full-CRUD {@code AdminStaffService}/{@code AdminUserRepository} in the parent {@code application}/
 * {@code domain} packages, which stay internal - mirrors {@code masterdata.application.lookup}'s own
 * split from its full-CRUD services, for the same reason (the parent package's classes must stay
 * public for sibling {@code identity.api.*} controllers to call them; a package-level named-interface
 * there would expose that whole broad surface instead of just this narrow one).
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.identity.application.lookup;
