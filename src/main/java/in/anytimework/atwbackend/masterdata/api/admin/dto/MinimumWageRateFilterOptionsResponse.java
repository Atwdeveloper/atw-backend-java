package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.SkillCategorySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Scoped filter-dropdown options for the minimum wage rate listing - only the "
        + "states/skill categories actually referenced by at least one rate row today, not every "
        + "masterdata state/skill-category.")
public record MinimumWageRateFilterOptionsResponse(
        List<StateSummary> states,
        List<SkillCategorySummary> skillCategories
) {
}
