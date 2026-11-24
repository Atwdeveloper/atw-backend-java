package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Filter-dropdown options scoped to values actually referenced by at least one non-deleted worker
 * today - never the full masterdata reference list. E.g. if only 3 states appear across every
 * worker's address, {@code states} here has exactly 3 entries, not every state in the country.
 */
@Schema(description = "Values actually present among today's workers, for populating the worker list's "
        + "filter dropdowns - never the full masterdata reference list.")
public record WorkerFilterOptionsResponse(
        List<IndustrySummary> industries,
        List<DesignationSummary> designations,
        List<SkillSummary> skills,
        List<StateSummary> states,
        List<CitySummary> cities
) {
}
