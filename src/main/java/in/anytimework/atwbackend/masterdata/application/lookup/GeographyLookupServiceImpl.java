package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.CityRepository;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class GeographyLookupServiceImpl implements GeographyLookupService {

    private final StateRepository states;
    private final CityRepository cities;

    GeographyLookupServiceImpl(StateRepository states, CityRepository cities) {
        this.states = states;
        this.cities = cities;
    }

    @Override
    public List<StateSummary> listStates() {
        return states.findAllByOrderByName().stream()
                .map(s -> new StateSummary(s.getId(), s.getName(), s.getCode()))
                .toList();
    }

    @Override
    public List<CitySummary> listCities(UUID stateId) {
        return cities.findByStateIdOrderByName(stateId).stream()
                .map(c -> new CitySummary(c.getId(), c.getName()))
                .toList();
    }

    @Override
    public boolean stateExists(UUID stateId) {
        return states.existsById(stateId);
    }

    @Override
    public boolean cityExists(UUID cityId) {
        return cities.existsById(cityId);
    }
}
