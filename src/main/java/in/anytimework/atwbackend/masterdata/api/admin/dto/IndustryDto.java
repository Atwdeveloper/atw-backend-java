package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.UUID;

public record IndustryDto(UUID id, String name, String code, ActiveStatus status, String imageUrl) {
}
