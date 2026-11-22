package in.anytimework.atwbackend.company.application;

import java.util.List;
import java.util.UUID;

/**
 * Everything {@code CompanyService.create} needs: the company's own fields plus the owner's details
 * for the single {@code CompanyUser} row this round creates (see {@code CompanyService}'s Javadoc -
 * only an OWNER is ever created, never a separate "company contact email" field, since the owner's
 * own email IS the company's practical contact point).
 *
 * <p>{@code gstNumber}/{@code panNumber} are optional - GST verification is always a separate, later
 * action ({@code CompanyService.triggerGstVerification}), never a precondition of creation.
 * {@code addresses} may be empty; when non-empty, exactly one entry must have
 * {@link CompanyAddressInput#primary()} true.</p>
 */
public record CreateCompanyCommand(
        String name,
        UUID industryId,
        String gstNumber,
        String panNumber,
        List<CompanyAddressInput> addresses,
        String ownerName,
        String ownerEmail,
        String ownerPhone
) {
}
