package in.anytimework.atwbackend.masterdata.api;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Masterdata - Geography", description = "Public (any authenticated caller, no specific "
        + "permission) read-only state/city lookups for address dropdowns - seeded once via Flyway, "
        + "never created/edited through this API. \"City\" here is district-grain terminology, a "
        + "deliberate product/UX choice, not a data-modeling one.")
@RestController
@RequestMapping(ApiVersions.V1 + "/masterdata")
public class MasterDataController {

    private final GeographyLookupService geography;

    public MasterDataController(GeographyLookupService geography) {
        this.geography = geography;
    }

    @Operation(summary = "List all states/UTs", description = "Returns all 36 Indian states/UTs, "
            + "sorted by name. Fixed Flyway-seeded reference data.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"data\":"
                            + "[{\"id\":\"1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d\",\"name\":\"Rajasthan\",\"code\":\"IN-RJ\"}],"
                            + "\"error\":null}")))
    })
    @GetMapping("/states")
    public ApiResponse<List<StateSummary>> listStates() {
        return ApiResponse.ok(geography.listStates());
    }

    @Operation(summary = "List cities (districts) in a state", description = "Returns every city "
            + "(district-grain) belonging to the given state, sorted by name.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"data\":"
                            + "[{\"id\":\"6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c\",\"name\":\"Jaipur\"}],\"error\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description =
                    "Unknown stateId returns an empty list, not an error - there is no RESOURCE_NOT_FOUND "
                            + "case here since this is a pure lookup, not a resource fetch.",
                    content = @Content(examples = @ExampleObject(name = "unknown-state",
                            value = "{\"success\":true,\"data\":[],\"error\":null}")))
    })
    @GetMapping("/states/{stateId}/cities")
    public ApiResponse<List<CitySummary>> listCities(
            @Parameter(description = "A state's id, from List States.", required = true)
            @Schema(example = "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d")
            @PathVariable UUID stateId) {
        return ApiResponse.ok(geography.listCities(stateId));
    }
}
