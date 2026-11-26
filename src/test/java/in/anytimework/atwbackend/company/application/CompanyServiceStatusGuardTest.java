package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.Company;
import in.anytimework.atwbackend.company.domain.CompanyAddressRepository;
import in.anytimework.atwbackend.company.domain.CompanyAddressType;
import in.anytimework.atwbackend.company.domain.CompanyAuthTokenRepository;
import in.anytimework.atwbackend.company.domain.CompanyRefreshTokenRepository;
import in.anytimework.atwbackend.company.domain.CompanyRepository;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.company.domain.CompanyStatusHistoryRepository;
import in.anytimework.atwbackend.company.domain.CompanyUserRepository;
import in.anytimework.atwbackend.company.domain.CompanyUserStatus;
import in.anytimework.atwbackend.identity.application.lookup.AdminActorLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.CompanyIdentityNumberHasher;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for {@link CompanyService}'s business-rule guards -
 * mirrors {@code WorkerServiceStatusGuardTest}'s style for a service whose rules don't need a real
 * database to verify. Covers every {@link CompanyStatus} transition (legal and illegal - see
 * {@link CompanyStatus}'s own Javadoc for the business reasoning behind each one), the
 * user-disable/refresh-token-revoke cascade a status change into/out of {@code ACTIVE} fires (the
 * single most security-critical behavior in this module - see {@link Company}'s class Javadoc), GST/PAN
 * duplicate-hash detection on {@link CompanyService#create}, and {@link CompanyService#triggerGstVerification}'s
 * success/failure handling.
 */
class CompanyServiceStatusGuardTest {

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyAddressRepository companyAddresses = mock(CompanyAddressRepository.class);
    private final CompanyStatusHistoryRepository statusHistory = mock(CompanyStatusHistoryRepository.class);
    private final CompanyUserRepository companyUsers = mock(CompanyUserRepository.class);
    private final CompanyAuthTokenRepository authTokens = mock(CompanyAuthTokenRepository.class);
    private final CompanyRefreshTokenRepository refreshTokens = mock(CompanyRefreshTokenRepository.class);
    private final IndustryLookupService industryLookup = mock(IndustryLookupService.class);
    private final GeographyLookupService geography = mock(GeographyLookupService.class);
    private final GstVerificationPort gstVerificationPort = mock(GstVerificationPort.class);
    private final CompanyIdentityNumberHasher identityHasher = mock(CompanyIdentityNumberHasher.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final PermissionChecker permissionChecker = mock(PermissionChecker.class);
    private final CurrentAdmin currentAdmin = mock(CurrentAdmin.class);
    private final AdminActorLookupService adminActorLookup = mock(AdminActorLookupService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T06:00:00Z"), ZoneOffset.UTC);
    private final CompanySecurityProperties securityProperties = new CompanySecurityProperties(
            true, Duration.ofMinutes(10), 6, 5, Duration.ofSeconds(60), 3,
            5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofDays(3), true, null);

    private final UUID industryId = UUID.randomUUID();
    private final Company company = new Company("CMP0000001", "Acme Builders", industryId, null, null, null, null);

    private CompanyService service() {
        return new CompanyService(companies, companyAddresses, statusHistory, companyUsers, authTokens,
                refreshTokens, industryLookup, geography, gstVerificationPort, identityHasher, passwordEncoder,
                notificationService, permissionChecker, securityProperties, currentAdmin, adminActorLookup, clock,
                "http://localhost:3000");
    }

    private void stubExistingCompany() {
        when(companies.findById(any())).thenReturn(Optional.of(company));
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyAddresses.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findByCompanyId(any())).thenReturn(List.of());
    }

    // ---- status transitions: every legal pair the spec allows ---------------------------------

    @Test
    void updateStatus_pendingVerificationToActive_isAllowed() {
        stubExistingCompany();
        assertThatCode(() -> service().updateStatus(company.getId(), CompanyStatus.ACTIVE, null))
                .doesNotThrowAnyException();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    void updateStatus_pendingVerificationToRejected_withReason_isAllowed() {
        stubExistingCompany();
        assertThatCode(() -> service().updateStatus(company.getId(), CompanyStatus.REJECTED, "Incomplete documents"))
                .doesNotThrowAnyException();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.REJECTED);
    }

    @Test
    void updateStatus_rejectedToPendingVerification_reasonOptional_isAllowed() {
        company.changeStatus(CompanyStatus.REJECTED, "Incomplete documents");
        stubExistingCompany();
        assertThatCode(() -> service().updateStatus(company.getId(), CompanyStatus.PENDING_VERIFICATION, null))
                .doesNotThrowAnyException();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
    }

    @Test
    void updateStatus_activeToSuspended_withReason_isAllowed() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();
        assertThatCode(() -> service().updateStatus(company.getId(), CompanyStatus.SUSPENDED, "Non-payment"))
                .doesNotThrowAnyException();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void updateStatus_suspendedToActive_isAllowed() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        company.changeStatus(CompanyStatus.SUSPENDED, "Non-payment");
        stubExistingCompany();
        assertThatCode(() -> service().updateStatus(company.getId(), CompanyStatus.ACTIVE, null))
                .doesNotThrowAnyException();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ---- status transitions: every illegal pair the spec explicitly forbids -------------------

    @Test
    void updateStatus_pendingVerificationToRejected_withoutReason_isRejected() {
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.REJECTED, "  "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void updateStatus_activeToSuspended_withoutReason_isRejected() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.SUSPENDED, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void updateStatus_rejectedToActive_isRejected_mustPassThroughPendingVerificationFirst() {
        company.changeStatus(CompanyStatus.REJECTED, "Incomplete documents");
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.ACTIVE, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot move");
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.REJECTED);
    }

    @Test
    void updateStatus_activeToRejected_isRejected_activeCompanyIsSuspendedNeverRejected() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.REJECTED, "Fraud"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot move");
    }

    @Test
    void updateStatus_suspendedToRejected_isRejected() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        company.changeStatus(CompanyStatus.SUSPENDED, "Non-payment");
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.REJECTED, "Fraud"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot move");
    }

    @Test
    void updateStatus_pendingVerificationToSuspended_isRejected() {
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.SUSPENDED, "Non-payment"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot move");
    }

    @Test
    void updateStatus_toSameStatusCompanyAlreadyHas_isRejected() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();
        assertThatThrownBy(() -> service().updateStatus(company.getId(), CompanyStatus.ACTIVE, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already");
    }

    @Test
    void updateStatus_unknownCompany_throwsResourceNotFound() {
        when(companies.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().updateStatus(UUID.randomUUID(), CompanyStatus.ACTIVE, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- the user-disable / refresh-token-revoke cascade (Company's class Javadoc) ------------

    @Test
    void updateStatus_toSuspended_disablesUsersAndRevokesRefreshTokens() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();
        UUID userId = UUID.randomUUID();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of(userId));

        service().updateStatus(company.getId(), CompanyStatus.SUSPENDED, "Non-payment");

        verify(companyUsers).updateStatusForCompany(any(), eq(CompanyUserStatus.DISABLED));
        verify(refreshTokens).revokeActiveForUsers(eq(List.of(userId)), any());
    }

    @Test
    void updateStatus_toRejected_disablesUsersAndRevokesRefreshTokens() {
        stubExistingCompany();
        UUID userId = UUID.randomUUID();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of(userId));

        service().updateStatus(company.getId(), CompanyStatus.REJECTED, "Fraud");

        verify(companyUsers).updateStatusForCompany(any(), eq(CompanyUserStatus.DISABLED));
        verify(refreshTokens).revokeActiveForUsers(eq(List.of(userId)), any());
    }

    @Test
    void updateStatus_toActive_fromSuspended_reactivatesUsers_withoutRevokingTokens() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        company.changeStatus(CompanyStatus.SUSPENDED, "Non-payment");
        stubExistingCompany();

        service().updateStatus(company.getId(), CompanyStatus.ACTIVE, null);

        verify(companyUsers).updateStatusForCompany(any(), eq(CompanyUserStatus.ACTIVE));
        verify(refreshTokens, never()).revokeActiveForUsers(any(), any());
    }

    @Test
    void disableCascade_withNoCompanyUsers_neverCallsRevoke() {
        // Guards the "never pass an empty collection to an IN (...) clause" rule
        // (CompanyRefreshTokenRepository.revokeActiveForUsers's own Javadoc).
        stubExistingCompany();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        service().updateStatus(company.getId(), CompanyStatus.REJECTED, "Fraud");

        verify(companyUsers).updateStatusForCompany(any(), eq(CompanyUserStatus.DISABLED));
        verify(refreshTokens, never()).revokeActiveForUsers(any(), any());
    }

    @Test
    void delete_disablesUsersAndRevokesRefreshTokens() {
        stubExistingCompany();
        UUID userId = UUID.randomUUID();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of(userId));

        service().delete(company.getId());

        assertThat(company.getDeletedAt()).isNotNull();
        verify(companyUsers).updateStatusForCompany(any(), eq(CompanyUserStatus.DISABLED));
        verify(refreshTokens).revokeActiveForUsers(eq(List.of(userId)), any());
    }

    // ---- delete(): the "must not be ACTIVE" precondition (mirrors WorkerService.delete's guard) ----

    @Test
    void delete_whileActive_isRejected() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        stubExistingCompany();

        assertThatThrownBy(() -> service().delete(company.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete a company while ACTIVE");

        assertThat(company.getDeletedAt()).isNull();
        verify(companies, never()).save(any());
        verify(companyUsers, never()).updateStatusForCompany(any(), any());
        verify(refreshTokens, never()).revokeActiveForUsers(any(), any());
    }

    @Test
    void delete_whileSuspended_isAllowed() {
        company.changeStatus(CompanyStatus.ACTIVE, null);
        company.changeStatus(CompanyStatus.SUSPENDED, "Non-payment");
        stubExistingCompany();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        assertThatCode(() -> service().delete(company.getId())).doesNotThrowAnyException();
        assertThat(company.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_whileRejected_isAllowed() {
        company.changeStatus(CompanyStatus.REJECTED, "Fraud");
        stubExistingCompany();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        assertThatCode(() -> service().delete(company.getId())).doesNotThrowAnyException();
        assertThat(company.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_whilePendingVerification_isAllowed() {
        // The shared fixture's `company` starts PENDING_VERIFICATION by construction - no status change needed.
        stubExistingCompany();
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        assertThatCode(() -> service().delete(company.getId())).doesNotThrowAnyException();
        assertThat(company.getDeletedAt()).isNotNull();
    }

    // ---- bulkUpdateStatus()/bulkDelete(): partial success - an illegal transition, a missing reason,
    // or an ACTIVE company inside a batch becomes a BulkActionFailure entry, never an exception
    // escaping the whole call -----------------------------------------------------------------------
    //
    // NOTE: a plain `new Company(...)` here is never assigned a real id (that only happens via
    // Hibernate's @UuidGenerator at actual persist time, which never runs in this Mockito-only test) -
    // every other test in this file works around that by sharing ONE fixture and stubbing with any().
    // A bulk call needs several DISTINCT, independently-stubbed companies in the same batch, so these
    // tests instead generate their own ids and stub companies.findById(thatId) directly - the id
    // CompanyService sees (and echoes back in BulkActionFailure.id()) is whatever key the mock was
    // stubbed against, never the (always-null) Company.getId() of a transient entity.

    private void stubCompanyById(UUID id, Company c) {
        when(companies.findById(id)).thenReturn(Optional.of(c));
    }

    @Test
    void bulkUpdateStatus_illegalTransitionInsideABatch_becomesAFailure_notAnException_otherIdsStillSucceed() {
        UUID succeedsId = UUID.randomUUID();
        UUID illegalTransitionId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        Company succeeds = new Company("CMP2000001", "Acme A", industryId, null, null, null, null);
        succeeds.changeStatus(CompanyStatus.ACTIVE, null); // ACTIVE -> SUSPENDED is a legal transition
        Company illegalTransition = new Company("CMP2000002", "Acme B", industryId, null, null, null, null);
        // Starts PENDING_VERIFICATION by construction - PENDING_VERIFICATION -> SUSPENDED is not an allowed transition.
        stubCompanyById(succeedsId, succeeds);
        stubCompanyById(illegalTransitionId, illegalTransition);
        when(companies.findById(unknownId)).thenReturn(Optional.empty());
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyAddresses.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        BulkActionResult result = service().bulkUpdateStatus(
                Set.of(succeedsId, illegalTransitionId, unknownId), CompanyStatus.SUSPENDED, "Non-payment");

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(illegalTransitionId, unknownId);
        assertThat(result.failures()).filteredOn(f -> f.id().equals(unknownId))
                .extracting(BulkActionFailure::reason).containsExactly("Not found");
        assertThat(result.failures()).filteredOn(f -> f.id().equals(illegalTransitionId))
                .extracting(BulkActionFailure::reason).first().satisfies(reason ->
                        assertThat((String) reason).contains("Cannot move"));
        // The one real success was actually applied, not merely counted.
        assertThat(succeeds.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(illegalTransition.getStatus()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
    }

    @Test
    void bulkUpdateStatus_missingReasonInsideABatch_becomesAFailure_notAnException() {
        UUID missingReasonId = UUID.randomUUID();
        Company missingReason = new Company("CMP2000003", "Acme C", industryId, null, null, null, null);
        missingReason.changeStatus(CompanyStatus.ACTIVE, null);
        stubCompanyById(missingReasonId, missingReason);

        BulkActionResult result = service().bulkUpdateStatus(Set.of(missingReasonId), CompanyStatus.SUSPENDED, "  ");

        assertThat(result.successCount()).isZero();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(missingReasonId);
        assertThat(result.failures().get(0).reason()).contains("reason is required");
        assertThat(missingReason.getStatus()).isEqualTo(CompanyStatus.ACTIVE); // unchanged
        verify(companies, never()).save(any());
    }

    @Test
    void bulkUpdateStatus_allSucceed_reportsFullSuccessAndNoFailures() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Company first = new Company("CMP2000004", "Acme D", industryId, null, null, null, null);
        first.changeStatus(CompanyStatus.ACTIVE, null);
        Company second = new Company("CMP2000005", "Acme E", industryId, null, null, null, null);
        second.changeStatus(CompanyStatus.ACTIVE, null);
        stubCompanyById(firstId, first);
        stubCompanyById(secondId, second);
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyAddresses.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        BulkActionResult result = service().bulkUpdateStatus(Set.of(firstId, secondId), CompanyStatus.SUSPENDED, "Non-payment");

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(first.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(second.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void bulkDelete_activeCompanyInsideABatch_becomesAFailure_notAnException_otherIdsStillSucceed() {
        UUID deletableId = UUID.randomUUID();
        UUID activeCompanyId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        Company deletable = new Company("CMP2000006", "Acme F", industryId, null, null, null, null);
        deletable.changeStatus(CompanyStatus.ACTIVE, null);
        deletable.changeStatus(CompanyStatus.SUSPENDED, "Non-payment");
        Company activeCompany = new Company("CMP2000007", "Acme G", industryId, null, null, null, null);
        activeCompany.changeStatus(CompanyStatus.ACTIVE, null);
        stubCompanyById(deletableId, deletable);
        stubCompanyById(activeCompanyId, activeCompany);
        when(companies.findById(unknownId)).thenReturn(Optional.empty());
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        BulkActionResult result = service().bulkDelete(Set.of(deletableId, activeCompanyId, unknownId));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(activeCompanyId, unknownId);
        assertThat(result.failures()).filteredOn(f -> f.id().equals(activeCompanyId))
                .extracting(BulkActionFailure::reason).first().satisfies(reason ->
                        assertThat((String) reason).contains("Cannot delete a company while ACTIVE"));
        assertThat(result.failures()).filteredOn(f -> f.id().equals(unknownId))
                .extracting(BulkActionFailure::reason).containsExactly("Not found");
        // The one real success was actually applied (soft-deleted), and the failed id was left untouched.
        assertThat(deletable.getDeletedAt()).isNotNull();
        assertThat(activeCompany.getDeletedAt()).isNull();
    }

    @Test
    void bulkDelete_allSucceed_reportsFullSuccessAndNoFailures() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Company first = new Company("CMP2000008", "Acme H", industryId, null, null, null, null);
        first.changeStatus(CompanyStatus.REJECTED, "Fraud");
        Company second = new Company("CMP2000009", "Acme I", industryId, null, null, null, null);
        second.changeStatus(CompanyStatus.REJECTED, "Fraud");
        stubCompanyById(firstId, first);
        stubCompanyById(secondId, second);
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyUsers.findIdsByCompanyId(any())).thenReturn(List.of());

        BulkActionResult result = service().bulkDelete(Set.of(firstId, secondId));

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(second.getDeletedAt()).isNotNull();
    }

    // ---- create(): GST/PAN duplicate-hash handling and other create-time guards ---------------

    private CreateCompanyCommand createCommand(String gst, String pan) {
        return new CreateCompanyCommand("Acme Builders", industryId, gst, pan, List.of(),
                "Ravi Shah", "ravi-" + UUID.randomUUID() + "@example.com", null);
    }

    private CompanyAddressInput address(boolean primary) {
        return new CompanyAddressInput(CompanyAddressType.REGISTERED_OFFICE, primary, "Line 1", null, null, null, null);
    }

    private void stubHappyPathForCreate() {
        when(industryLookup.exists(any())).thenReturn(true);
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyUsers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(companyAddresses.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findByCompanyId(any())).thenReturn(List.of());
        when(companies.nextCompanyCodeSequenceValue()).thenReturn(1L);
    }

    @Test
    void create_withDuplicateGstHash_throwsConflict() {
        stubHappyPathForCreate();
        when(identityHasher.hash("27AAAPG1234L1ZV")).thenReturn("hash-gst");
        when(companies.existsByGstNumberHash("hash-gst")).thenReturn(true);

        assertThatThrownBy(() -> service().create(createCommand("27AAAPG1234L1ZV", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("GST");
    }

    @Test
    void create_withDuplicatePanHash_throwsConflict() {
        stubHappyPathForCreate();
        when(identityHasher.hash("AAAPG1234L")).thenReturn("hash-pan");
        when(companies.existsByPanNumberHash("hash-pan")).thenReturn(true);

        assertThatThrownBy(() -> service().create(createCommand(null, "AAAPG1234L")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PAN");
    }

    @Test
    void create_withBlankGstAndPan_neverHashesOrChecksDuplicates() {
        stubHappyPathForCreate();

        service().create(createCommand(null, "   "));

        verify(identityHasher, never()).hash(any());
        verify(companies, never()).existsByGstNumberHash(any());
        verify(companies, never()).existsByPanNumberHash(any());
    }

    @Test
    void create_withUnknownIndustry_throwsValidation() {
        when(industryLookup.exists(any())).thenReturn(false);
        assertThatThrownBy(() -> service().create(createCommand(null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withDuplicateOwnerEmail_throwsConflict() {
        when(industryLookup.exists(any())).thenReturn(true);
        when(companyUsers.existsByEmailIgnoreCase(any())).thenReturn(true);

        assertThatThrownBy(() -> service().create(createCommand(null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withMultiplePrimaryAddresses_throwsValidation() {
        stubHappyPathForCreate();
        CreateCompanyCommand command = new CreateCompanyCommand("Acme", industryId, null, null,
                List.of(address(true), address(true)), "Ravi", "ravi-" + UUID.randomUUID() + "@example.com", null);

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void create_withNoPrimaryAddressAmongNonEmptyList_throwsValidation() {
        stubHappyPathForCreate();
        CreateCompanyCommand command = new CreateCompanyCommand("Acme", industryId, null, null,
                List.of(address(false)), "Ravi", "ravi-" + UUID.randomUUID() + "@example.com", null);

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Exactly one");
    }

    @Test
    void create_withUnknownState_throwsValidation() {
        stubHappyPathForCreate();
        UUID stateId = UUID.randomUUID();
        when(geography.stateExists(stateId)).thenReturn(false);
        CompanyAddressInput addr = new CompanyAddressInput(CompanyAddressType.REGISTERED_OFFICE, true, "Line 1",
                null, stateId, null, null);
        CreateCompanyCommand command = new CreateCompanyCommand("Acme", industryId, null, null, List.of(addr),
                "Ravi", "ravi-" + UUID.randomUUID() + "@example.com", null);

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown state");
    }

    @Test
    void create_withUnknownCity_throwsValidation() {
        stubHappyPathForCreate();
        UUID cityId = UUID.randomUUID();
        when(geography.cityExists(cityId)).thenReturn(false);
        CompanyAddressInput addr = new CompanyAddressInput(CompanyAddressType.REGISTERED_OFFICE, true, "Line 1",
                null, null, cityId, null);
        CreateCompanyCommand command = new CreateCompanyCommand("Acme", industryId, null, null, List.of(addr),
                "Ravi", "ravi-" + UUID.randomUUID() + "@example.com", null);

        assertThatThrownBy(() -> service().create(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown city");
    }

    @Test
    void create_sendsOwnerInviteViaEnqueue_notSynchronousSend() {
        // Deliberate deviation from the admin-staff invite mirror (CompanyService.create's Javadoc):
        // a transient email-provider hiccup must never roll back the whole company+owner creation.
        stubHappyPathForCreate();

        service().create(createCommand(null, null));

        verify(notificationService).enqueue(any(SendEmailCommand.class));
        verify(notificationService, never()).send(any());
    }

    @Test
    void create_startsAsPendingVerificationWithGstUnverified() {
        stubHappyPathForCreate();

        CompanyView view = service().create(createCommand(null, null));

        assertThat(view.status()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
        assertThat(view.gstVerifiedAt()).isNull();
    }

    @Test
    void create_generatesCompanyCodeWithCmpPrefixAndSevenDigits() {
        stubHappyPathForCreate();
        when(companies.nextCompanyCodeSequenceValue()).thenReturn(42L);

        CompanyView view = service().create(createCommand(null, null));

        assertThat(view.companyCode()).isEqualTo("CMP0000042");
    }

    // ---- triggerGstVerification(): success/failure handling -----------------------------------

    @Test
    void triggerGstVerification_noGstNumberOnFile_throwsValidation() {
        stubExistingCompany(); // the shared fixture's gstNumber is null by construction
        assertThatThrownBy(() -> service().triggerGstVerification(company.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no GST number");
    }

    @Test
    void triggerGstVerification_providerReportsUnverified_throwsBusinessRuleException_andPersistsNothing() {
        Company withGst = new Company("CMP0000002", "Acme", industryId, "27AAAPG1234L1ZV", "hash", null, null);
        when(companies.findById(any())).thenReturn(Optional.of(withGst));
        when(gstVerificationPort.verify("27AAAPG1234L1ZV"))
                .thenReturn(new GstVerificationResult(false, null, null, null, "GSTIN not found"));

        assertThatThrownBy(() -> service().triggerGstVerification(withGst.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("GSTIN not found");

        assertThat(withGst.getGstVerifiedAt()).isNull();
        verify(companies, never()).save(any());
    }

    @Test
    void triggerGstVerification_success_setsVerifiedAtAndRef() {
        Company withGst = new Company("CMP0000003", "Acme", industryId, "27AAAPG1234L1ZV", "hash", null, null);
        when(companies.findById(any())).thenReturn(Optional.of(withGst));
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyAddresses.findByCompanyId(any())).thenReturn(List.of());
        when(companyUsers.findByCompanyId(any())).thenReturn(List.of());
        when(gstVerificationPort.verify("27AAAPG1234L1ZV"))
                .thenReturn(new GstVerificationResult(true, "Acme Builders Pvt Ltd", "Maharashtra", "REF-123", null));

        CompanyView view = service().triggerGstVerification(withGst.getId());

        assertThat(withGst.getGstVerifiedAt()).isNotNull();
        assertThat(withGst.getGstVerificationRef()).isEqualTo("REF-123");
        assertThat(view.gstVerificationRef()).isEqualTo("REF-123");
    }
}
