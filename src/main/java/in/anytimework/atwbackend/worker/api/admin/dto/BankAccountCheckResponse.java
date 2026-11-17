package in.anytimework.atwbackend.worker.api.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Advisory-only result of a standalone bank-account check - nothing is "
        + "persisted. To make a verification authoritative for a specific worker (set "
        + "verified/verifiedAt on their bank detail), use POST /{id}/bank-detail/verify instead, "
        + "once the worker and their bank detail row exist.")
public record BankAccountCheckResponse(
        @Schema(description = "Whether the provider confirmed the account number + IFSC combination is real/active.")
        boolean exists,
        @Schema(description = "Whether the bank's name-at-account (when the provider returns one) "
                + "reasonably matches (case/whitespace-insensitive) the given accountHolderName. "
                + "True when exists is true and the provider doesn't return a name to compare at all.")
        boolean nameMatched,
        @Schema(description = "The account holder name the bank has on file, as the provider returned "
                + "it - null if the provider doesn't return one, or if exists is false.", nullable = true)
        String nameAtBank
) {
}
