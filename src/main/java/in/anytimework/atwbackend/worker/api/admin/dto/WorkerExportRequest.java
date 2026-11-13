package in.anytimework.atwbackend.worker.api.admin.dto;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

/**
 * Body for the CSV export endpoint. When {@code ids} is given and non-empty, exports exactly those
 * workers; otherwise exports every worker matching the remaining fields, which mirror the admin list
 * endpoint's own filters exactly (see {@code WorkerSearchCriteria}) - moved to a POST body instead of
 * query params since a filtered full-table export isn't a single resource "get". Posting {@code {}}
 * (every field absent) exports every worker, unfiltered.
 */
@Schema(description = "Selects which workers the CSV export includes. Give ids for an exact set "
        + "(unknown ids are silently skipped); otherwise every other field is an optional filter with "
        + "the exact same semantics as GET / (a non-empty set matches ANY of its values; every "
        + "non-empty field is ANDed together). An empty body ({}) exports every worker.")
public record WorkerExportRequest(
        @Schema(description = "Exact set of worker ids to export. When given and non-empty, every "
                + "filter field below is ignored.", nullable = true) Set<UUID> ids,
        @Schema(nullable = true) Set<WorkerStatus> status,
        @Schema(description = "Department ids (Masterdata module).", nullable = true) Set<UUID> departmentId,
        @Schema(description = "Industry ids (Masterdata module).", nullable = true) Set<UUID> industryId,
        @Schema(description = "Designation ids (Masterdata module).", nullable = true) Set<UUID> designationId,
        @Schema(description = "Skill ids (Masterdata module).", nullable = true) Set<UUID> skillId,
        @Schema(description = "KYC statuses, resolved from the separate kyc module.", nullable = true)
        Set<KycStatus> kycStatus,
        @Schema(description = "Free-text match against first/last name, mobile number, worker code, "
                + "and email.", nullable = true, example = "sharma") String search
) {
}
