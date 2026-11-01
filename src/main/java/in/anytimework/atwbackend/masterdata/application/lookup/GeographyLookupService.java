package in.anytimework.atwbackend.masterdata.application.lookup;

import java.util.List;
import java.util.UUID;

/**
 * Public API of the masterdata module. Other modules hold only a state/city UUID on their own
 * address records and call this to resolve or validate it - never a direct JPA relationship into
 * {@code State}/{@code City}.
 */
public interface GeographyLookupService {

    List<StateSummary> listStates();

    List<CitySummary> listCities(UUID stateId);

    boolean stateExists(UUID stateId);

    boolean cityExists(UUID cityId);
}
