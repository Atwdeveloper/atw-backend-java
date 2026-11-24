package in.anytimework.atwbackend.identity.api.staff.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Scoped filter-dropdown options for the staff list - only the states/cities/roles actually
 * referenced by at least one staff account today, never the full masterdata/role catalog a bare
 * list endpoint would return. Mirrors {@code worker.api.admin.dto.WorkerFilterOptionsResponse}'s
 * same purpose for the worker list.
 */
@Schema(description = "Scoped filter-dropdown options for the staff list - only values actually "
        + "referenced by at least one staff account today, not the full masterdata/role catalog.")
public record AdminStaffFilterOptionsResponse(
        @Schema(description = "States referenced by at least one staff member's saved address.")
        List<StateSummary> states,
        @Schema(description = "Cities referenced by at least one staff member's saved address.")
        List<CitySummary> cities,
        @Schema(description = "Roles currently assigned to at least one staff member.")
        List<AdminRoleRef> roles
) {
}
