package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.UUID;

public record SkillDto(UUID id, String name, UUID designationId, ActiveStatus status, String imageUrl) {
}
