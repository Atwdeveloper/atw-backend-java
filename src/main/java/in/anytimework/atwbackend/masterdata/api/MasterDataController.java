package in.anytimework.atwbackend.masterdata.api;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only reference data for address dropdowns. Identical for every actor (admin, and later
 * company/worker), so this is not nested under {@code /admin}, {@code /company}, etc. Requires
 * authentication (the {@link in.anytimework.atwbackend.shared.security.SecurityConfig} default)
 * but no specific permission - it is non-sensitive reference data.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/masterdata")
public class MasterDataController {

    private final GeographyLookupService geography;

    public MasterDataController(GeographyLookupService geography) {
        this.geography = geography;
    }

    @GetMapping("/states")
    public ApiResponse<List<StateSummary>> listStates() {
        return ApiResponse.ok(geography.listStates());
    }

    @GetMapping("/states/{stateId}/cities")
    public ApiResponse<List<CitySummary>> listCities(@PathVariable UUID stateId) {
        return ApiResponse.ok(geography.listCities(stateId));
    }
}
