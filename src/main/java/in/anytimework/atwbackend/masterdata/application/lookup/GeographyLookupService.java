package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the masterdata module. Other modules hold only a state/city UUID on their own
 * address records and call this to resolve or validate it - never a direct JPA relationship into
 * {@code State}/{@code City}.
 */
public interface GeographyLookupService {

    List<StateSummary> listStates();

    List<CitySummary> listCities(UUID stateId);

    /**
     * Resolves exactly the given city ids (one query, {@code WHERE id IN (...)}) - unlike
     * {@link #listStates()}, the full city table is never loaded: India has far more cities than
     * states, so "load everything into a map" (fine for states, departments, designations - all
     * small masterdata lists) would not scale here. Unknown ids are silently omitted from the result.
     */
    List<CitySummary> listCitiesByIds(Set<UUID> cityIds);

    boolean stateExists(UUID stateId);

    boolean cityExists(UUID cityId);
}
