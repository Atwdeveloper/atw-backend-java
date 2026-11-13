package in.anytimework.atwbackend.shared.api;

import java.util.List;

/**
 * Standard result envelope for a bulk operation across every module - partial success is the normal,
 * expected case (e.g. one id in the batch is still referenced elsewhere and can't be deleted), not an
 * all-or-nothing failure. Matches the shape legacy's own bulk endpoints already settled on
 * ({@code updated_count} / {@code {deleted:[], skipped:[{id,reason}]}}), generalized once here instead
 * of each module reinventing it.
 */
public record BulkActionResult(int successCount, List<BulkActionFailure> failures) {

    public static BulkActionResult allSucceeded(int count) {
        return new BulkActionResult(count, List.of());
    }
}
