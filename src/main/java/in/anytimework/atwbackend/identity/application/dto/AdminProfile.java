package in.anytimework.atwbackend.identity.application.dto;

import java.util.List;
import java.util.UUID;

/** The authenticated admin's identity + effective access, returned to clients. */
public record AdminProfile(
        UUID id,
        String username,
        String email,
        String fullName,
        boolean superAdmin,
        List<String> roles,
        List<String> authorities,
        boolean mustChangePassword
) {
}
