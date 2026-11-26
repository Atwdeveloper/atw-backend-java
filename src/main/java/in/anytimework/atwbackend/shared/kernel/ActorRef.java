package in.anytimework.atwbackend.shared.kernel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Who performed a tracked action, resolved for display - replaces showing a raw actor id (or a
 * generic JPA-auditing "username" string that was never actually a username) directly to an end user.
 * {@code id} is null exactly when {@code userType} is {@link ActorType#SYSTEM} - there is no principal
 * to point at for an automatic transition.
 */
@Schema(description = "Who performed this action, resolved for display.")
public record ActorRef(
        @Schema(description = "The acting admin/company user's id, or null for a SYSTEM-triggered action.",
                nullable = true) UUID id,
        @Schema(description = "Display name, or \"System\" for a SYSTEM-triggered action.", example = "Rahul Prajapat")
        String name,
        @Schema(description = "The kind of actor - ADMIN, SYSTEM (automatic, no principal involved), or "
                + "COMPANY.") ActorType userType
) {
    public static final ActorRef SYSTEM = new ActorRef(null, "System", ActorType.SYSTEM);
}
