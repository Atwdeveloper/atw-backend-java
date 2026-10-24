package in.anytimework.atwbackend.identity.application.dto;

/** The token pair + profile returned after a successful authentication. */
public record AuthTokens(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        AdminProfile admin
) {
}
