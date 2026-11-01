package in.anytimework.atwbackend.masterdata.api.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSkillCategoryRequest(@NotNull UUID skillCategoryId) {
}
