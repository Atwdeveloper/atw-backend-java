package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminRoleRef;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffFilterOptionsResponse;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserAddress;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.shared.persistence.DistinctColumnValues;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scoped filter-dropdown options for the staff list - mirrors
 * {@code worker.application.WorkerFilterOptionsService}'s exact pattern, applied to staff's own
 * filterable fields (state/city/role instead of industry/designation/skill/state/city).
 *
 * <p>{@code stateId}/{@code cityId} live on {@link AdminUserAddress}, which carries no
 * {@code @SQLRestriction} of its own (unlike {@link AdminUser}), so that half needs an extra "parent
 * admin still exists and isn't soft-deleted" guard predicate - same subquery technique
 * {@code WorkerFilterOptionsService.parentWorkerVisible()} already uses. {@code roleId} is a plain
 * {@code @ManyToMany} collection directly on {@code AdminUser} itself (no separate join entity, unlike
 * worker's designation/skill assignments) - a join rooted on {@code AdminUser} already gets its
 * {@code @SQLRestriction} applied for free, so no separate guard is needed for that part, and
 * {@link DistinctColumnValues} doesn't apply either (it walks a single entity's own
 * columns/embedded path, not a collection join) - a small dedicated repository query handles it
 * instead.</p>
 */
@Service
public class AdminStaffFilterOptionsService {

    private final EntityManager entityManager;
    private final AdminUserRepository adminUsers;
    private final AdminRoleRepository adminRoles;
    private final GeographyLookupService geography;

    public AdminStaffFilterOptionsService(EntityManager entityManager, AdminUserRepository adminUsers,
                                          AdminRoleRepository adminRoles, GeographyLookupService geography) {
        this.entityManager = entityManager;
        this.adminUsers = adminUsers;
        this.adminRoles = adminRoles;
        this.geography = geography;
    }

    @Transactional(readOnly = true)
    public AdminStaffFilterOptionsResponse get() {
        Set<UUID> stateIds = Set.copyOf(DistinctColumnValues.of(entityManager, AdminUserAddress.class,
                "address.stateId", UUID.class, List.of(parentAdminVisible())));
        Set<UUID> cityIds = Set.copyOf(DistinctColumnValues.of(entityManager, AdminUserAddress.class,
                "address.cityId", UUID.class, List.of(parentAdminVisible())));
        List<UUID> roleIds = adminUsers.findDistinctAssignedRoleIds();

        List<StateSummary> states = geography.listStates().stream()
                .filter(s -> stateIds.contains(s.id())).toList();
        List<CitySummary> cities = geography.listCitiesByIds(cityIds);
        List<AdminRoleRef> roles = adminRoles.findAllById(roleIds).stream()
                .map(r -> new AdminRoleRef(r.getId(), r.getName()))
                .sorted(Comparator.comparing(AdminRoleRef::name))
                .toList();

        return new AdminStaffFilterOptionsResponse(states, cities, roles);
    }

    private static <E> Specification<E> parentAdminVisible() {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<AdminUser> admin = subquery.from(AdminUser.class);
            subquery.select(admin.get("id"));
            return root.get("adminUserId").in(subquery);
        };
    }
}
