package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full detail for a single company - returned by every single-record {@code CompanyService} method
 * (get/create/update/updateStatus/triggerGstVerification). {@code gstNumber}/{@code panNumber} come
 * back masked ({@code shared.security.PiiMasking.maskTail}) unless the caller holds
 * {@code company.view_sensitive} - decided inside {@code CompanyService} itself (it injects
 * {@code PermissionChecker} directly), mirroring {@code kyc.application.KycServiceImpl.toDto}'s
 * exact precedent, not left to the controller.
 *
 * <p>Never used for a list row - see {@link CompanySummaryView} for why: batch-loading an owner ref
 * and the full address list for every row of a paginated list would be an N+1 query pattern this
 * codebase's {@code WorkerService}/{@code AdminStaffService} precedent always avoids via batch
 * resolution. A single-record path building 1-2 extra queries is the same accepted cost
 * {@code WorkerService.addressView}'s own Javadoc documents.</p>
 */
public record CompanyView(
        UUID id,
        String companyCode,
        String name,
        UUID industryId,
        String gstNumber,
        String panNumber,
        Instant gstVerifiedAt,
        String gstVerificationRef,
        CompanyStatus status,
        String statusReason,
        CompanyOwnerView owner,
        List<CompanyAddressView> addresses,
        Instant createdAt
) {
}
