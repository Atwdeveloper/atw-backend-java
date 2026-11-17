package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Filtering for the admin role list - only two dimensions exist (free-text search, system-role
 * flag), far less than {@code WorkerSpecifications}/{@code AdminStaffSpecifications} need, but kept
 * as the same {@link Specification}-per-filter shape for consistency with every other listing in the
 * codebase rather than a one-off query method.
 */
final class AdminRoleSpecifications {

    private AdminRoleSpecifications() {
    }

    static Specification<AdminRole> matchesSearch(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                // description is nullable - cb.lower(null column) yields NULL, which LIKE never
                // matches, so no extra isNotNull guard is needed (same reasoning as
                // WorkerSpecifications.matchesSearch's personalEmail column).
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    static Specification<AdminRole> isSystemRole(boolean systemRole) {
        return (root, query, cb) -> cb.equal(root.get("systemRole"), systemRole);
    }
}
