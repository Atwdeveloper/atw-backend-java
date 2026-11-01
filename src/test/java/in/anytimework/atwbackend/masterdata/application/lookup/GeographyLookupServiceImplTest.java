package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.City;
import in.anytimework.atwbackend.masterdata.domain.CityRepository;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for geography lookups (no Spring, no real DB). */
class GeographyLookupServiceImplTest {

    private final StateRepository states = mock(StateRepository.class);
    private final CityRepository cities = mock(CityRepository.class);
    private final GeographyLookupServiceImpl service = new GeographyLookupServiceImpl(states, cities);

    @Test
    void listStates_mapsEntitiesToSummaries() {
        when(states.findAllByOrderByName()).thenReturn(List.of(new State("Kerala", "KL")));

        List<StateSummary> result = service.listStates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Kerala");
        assertThat(result.get(0).code()).isEqualTo("KL");
    }

    @Test
    void listCities_returnsOnlyCitiesOfTheRequestedState() {
        UUID stateId = UUID.randomUUID();
        when(cities.findByStateIdOrderByName(stateId)).thenReturn(List.of(new City(stateId, "Jaipur")));

        List<CitySummary> result = service.listCities(stateId);

        assertThat(result).extracting(CitySummary::name).containsExactly("Jaipur");
    }

    @Test
    void stateExists_delegatesToRepository() {
        UUID stateId = UUID.randomUUID();
        when(states.existsById(stateId)).thenReturn(true);

        assertThat(service.stateExists(stateId)).isTrue();
    }

    @Test
    void cityExists_delegatesToRepository() {
        UUID cityId = UUID.randomUUID();
        when(cities.existsById(cityId)).thenReturn(false);

        assertThat(service.cityExists(cityId)).isFalse();
    }
}
