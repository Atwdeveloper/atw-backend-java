package in.anytimework.atwbackend.masterdata.api.admin.dto;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;

import java.util.UUID;

public record DepartmentDto(UUID id, String name, ActiveStatus status) {
}
