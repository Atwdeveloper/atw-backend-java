package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.domain.AdminStatus;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic multi-field filtering for the admin staff list - mirrors
 * {@code worker.application.WorkerSpecifications}'s conventions exactly (same {@link Specification}
 * mechanism, already part of {@code spring-data-jpa}, composed per non-empty filter).
 *
 * <p>The role filter deliberately queries the {@code admin_user_role} join table via a {@link
 * Subquery} rather than {@code root.join("roles")}: a direct collection join would duplicate the
 * parent row once per matching role, corrupting {@code Pageable}'s {@code totalElements}/content for
 * any staff member holding more than one of the selected roles. Same technique as
 * {@code WorkerSpecifications.hasDesignation}/{@code hasSkill}, applied here to a {@code @ManyToMany}
 * collection instead of a module-local join table.</p>
 */
final class AdminStaffSpecifications {

    private AdminStaffSpecifications() {
    }

    static Specification<AdminUser> hasStatus(Set<AdminStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<AdminUser> hasRole(Set<UUID> roleIds) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var adminUser = subquery.from(AdminUser.class);
            var role = adminUser.join("roles");
            subquery.select(adminUser.get("id")).where(role.get("id").in(roleIds));
            return root.get("id").in(subquery);
        };
    }

    static Specification<AdminUser> isSuperAdmin(boolean superAdmin) {
        return (root, query, cb) -> cb.equal(root.get("superAdmin"), superAdmin);
    }

    static Specification<AdminUser> matchesSearch(String term) {
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern),
                // username/phone are nullable - cb.lower(null column) yields NULL, which LIKE never
                // matches, so no extra isNotNull guard is needed here (same reasoning as
                // WorkerSpecifications.matchesSearch's personalEmail column).
                cb.like(cb.lower(root.get("username")), pattern),
                cb.like(root.get("phone"), pattern)
        );
    }
}
