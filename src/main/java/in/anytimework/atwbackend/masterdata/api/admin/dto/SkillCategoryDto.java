package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.UUID;

public record SkillCategoryDto(UUID id, String name, String code, ActiveStatus status) {
}
