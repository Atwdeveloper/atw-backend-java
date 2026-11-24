package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Filter-dropdown options scoped to values actually referenced by at least one non-deleted company
 * today - never the full masterdata reference list. E.g. if only 2 states appear across every
 * company's address, {@code states} here has exactly 2 entries, not every state in the country.
 */
@Schema(description = "Values actually present among today's companies, for populating the company list's "
        + "filter dropdowns - never the full masterdata reference list.")
public record CompanyFilterOptionsResponse(
        List<IndustrySummary> industries,
        List<StateSummary> states,
        List<CitySummary> cities
) {
}
