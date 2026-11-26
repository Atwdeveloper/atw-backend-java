package in.anytimework.atwbackend.identity.application.lookup;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves admin ids to display names for other modules showing "who did this" (worker/company
 * status history, worker notes, ...) - the one sanctioned way to turn an admin id into a name without
 * reaching into {@code identity}'s own repository directly.
 */
public interface AdminActorLookupService {

    /** Ids with no matching (or soft-deleted) admin are simply absent from the result - callers fall
     *  back to a generic label rather than this throwing or returning a placeholder string. */
    Map<UUID, String> findNames(Set<UUID> adminIds);
}
