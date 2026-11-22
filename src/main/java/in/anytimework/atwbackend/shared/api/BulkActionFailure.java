package in.anytimework.atwbackend.shared.api;

import java.util.UUID;

public record BulkActionFailure(UUID id, String reason) {
}
