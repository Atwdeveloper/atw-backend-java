package in.anytimework.atwbackend.company.api.admin.dto;

import in.anytimework.atwbackend.company.domain.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Body for the Excel export endpoint. When {@code ids} is given and non-empty, exports exactly those
 * companies; otherwise exports every company matching the remaining fields, which mirror the admin
 * list endpoint's own filters exactly (see {@code CompanySearchCriteria}) - moved to a POST body
 * instead of query params since a filtered full-table export isn't a single resource "get". Posting
 * {@code {}} (every field absent) exports every company, unfiltered.
 */
@Schema(description = "Selects which companies the Excel export includes. Give ids for an exact set "
        + "(unknown ids are silently skipped); otherwise every other field is an optional filter with "
        + "the exact same semantics as List Companies (a non-empty set matches ANY of its values; every "
        + "non-empty field is ANDed together). An empty body ({}) exports every company.")
public record CompanyExportRequest(
        @Schema(description = "Exact set of company ids to export. When given and non-empty, every "
                + "filter field below is ignored.", nullable = true) Set<UUID> ids,
        @Schema(nullable = true) Set<CompanyStatus> status,
        @Schema(description = "Industry ids (Masterdata module).", nullable = true) Set<UUID> industryId,
        @Schema(description = "Case-insensitive substring match against name or company code.",
                nullable = true, example = "Acme") String search,
        @Schema(description = "true -> GST-verified companies only, false -> unverified only, omit for "
                + "both.", nullable = true) Boolean gstVerified,
        @Schema(description = "Created on or after this date (UTC).", nullable = true, example = "2026-01-01") LocalDate createdFrom,
        @Schema(description = "Created on or before this date (UTC).", nullable = true, example = "2026-12-31") LocalDate createdTo,
        @Schema(description = "State ids (Masterdata module). Matches a company with ANY address "
                + "referencing any of these.", nullable = true) Set<UUID> stateId,
        @Schema(description = "City ids (Masterdata module). Matches a company with ANY address "
                + "referencing any of these.", nullable = true) Set<UUID> cityId
) {
}
