package in.anytimework.atwbackend.shared.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

/** Request body shared by every bulk-delete/bulk-export endpoint across every module - just a set of ids. */
public record BulkIdsRequest(@NotEmpty Set<UUID> ids) {
}
