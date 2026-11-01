package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.List;
import java.util.UUID;

public record DesignationDto(UUID id, String name, ActiveStatus status, String imageUrl, List<IndustrySummary> industries,
                              UUID skillCategoryId) {
}
