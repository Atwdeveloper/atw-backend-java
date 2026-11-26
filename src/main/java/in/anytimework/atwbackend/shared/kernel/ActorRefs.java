package in.anytimework.atwbackend.shared.kernel;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link ActorRef}s from the two shapes "who did this" shows up in across this codebase: a
 * real, nullable admin-id column (e.g. {@code WorkerStatusHistory.changedByAdminId},
 * {@code CompanyStatusHistory.changedByAdminId} - null means SYSTEM), or a raw JPA-auditing
 * {@code createdBy} string (e.g. {@code WorkerNote}, always either a parseable admin-id UUID or the
 * literal {@code "system"} - see {@code shared.persistence.SecurityAuditorAware}). Every caller
 * batch-resolves names once per page via {@code identity.application.lookup.AdminActorLookupService},
 * never once per row.
 */
public final class ActorRefs {

    private static final String SYSTEM_AUDITOR = "system";

    private ActorRefs() {
    }

    /** From a real, nullable admin-id column - null means SYSTEM. */
    public static ActorRef fromAdminId(UUID adminId, Map<UUID, String> namesById) {
        if (adminId == null) {
            return ActorRef.SYSTEM;
        }
        return new ActorRef(adminId, namesById.getOrDefault(adminId, "Unknown admin"), ActorType.ADMIN);
    }

    /** From a raw {@code createdBy}-style auditing string - "system" or an unparseable value both
     *  resolve to SYSTEM (defensive: this method's contract doesn't depend on every past value having
     *  been written by the exact current auditing convention). */
    public static ActorRef fromAuditString(String createdBy, Map<UUID, String> namesById) {
        UUID adminId = parseAdminId(createdBy);
        return adminId == null ? ActorRef.SYSTEM : fromAdminId(adminId, namesById);
    }

    /** The admin ids actually worth resolving out of a batch of raw {@code createdBy} values - feed
     *  this straight into {@code AdminActorLookupService.findNames}. */
    public static Set<UUID> parseableAdminIds(Collection<String> createdByValues) {
        return createdByValues.stream().map(ActorRefs::parseAdminId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static UUID parseAdminId(String createdBy) {
        if (createdBy == null || SYSTEM_AUDITOR.equals(createdBy)) {
            return null;
        }
        try {
            return UUID.fromString(createdBy);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
