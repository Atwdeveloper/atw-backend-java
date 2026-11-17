package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.worker.api.admin.dto.BankAccountCheckResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerBankDetailResponse;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetail;
import in.anytimework.atwbackend.worker.domain.WorkerBankDetailRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for {@code WorkerBankDetailService.verify} - mirrors
 * {@code WorkerServiceStatusGuardTest}'s style. Covers the match-policy decision (does the bank's
 * name-at-account sufficiently match the declared holder name) with a mocked {@link BankVerificationPort},
 * since the default {@code StubBankVerificationAdapter} never itself returns a name to compare and a
 * real vendor isn't wired up - see that adapter's Javadoc.
 */
class WorkerBankDetailServiceTest {

    private final WorkerRepository workers = mock(WorkerRepository.class);
    private final WorkerBankDetailRepository bankDetails = mock(WorkerBankDetailRepository.class);
    private final PermissionChecker permissionChecker = mock(PermissionChecker.class);
    private final BankVerificationPort bankVerificationPort = mock(BankVerificationPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneOffset.UTC);

    private final WorkerBankDetailService service =
            new WorkerBankDetailService(workers, bankDetails, permissionChecker, bankVerificationPort, clock);

    private final UUID workerId = UUID.randomUUID();

    private WorkerBankDetail existingDetail(String accountHolderName) {
        WorkerBankDetail detail = new WorkerBankDetail(workerId);
        detail.update(accountHolderName, "SBI", "31234567890", "SBIN0001234", "Main Branch");
        return detail;
    }

    @Test
    void verify_whenNoBankDetailOnFileYet_throwsBusinessRuleExceptionAndNeverCallsThePort() {
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(workerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No bank details on file");
        verify(bankVerificationPort, never()).verify(anyString(), anyString());
    }

    @Test
    void verify_whenProviderSaysAccountDoesNotExist_throwsBusinessRuleExceptionAndDoesNotMarkVerified() {
        WorkerBankDetail detail = existingDetail("Ramesh Kumar");
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.of(detail));
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(false, null, "ref-1"));

        assertThatThrownBy(() -> service.verify(workerId)).isInstanceOf(BusinessRuleException.class);
        assertThat(detail.isVerified()).isFalse();
    }

    @Test
    void verify_whenNameAtBankDoesNotMatchDeclaredHolderName_throwsConflictExceptionAndDoesNotMarkVerified() {
        WorkerBankDetail detail = existingDetail("Ramesh Kumar");
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.of(detail));
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, "Someone Else Entirely", "ref-2"));

        assertThatThrownBy(() -> service.verify(workerId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not match");
        assertThat(detail.isVerified()).isFalse();
    }

    @Test
    void verify_whenNameMatchesIgnoringCaseAndExtraWhitespace_marksVerified() {
        WorkerBankDetail detail = existingDetail("Ramesh Kumar");
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.of(detail));
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, "  ramesh   kumar  ", "ref-3"));
        when(bankDetails.save(detail)).thenReturn(detail);

        WorkerBankDetailResponse response = service.verify(workerId);

        assertThat(detail.isVerified()).isTrue();
        assertThat(detail.getVerifiedAt()).isEqualTo(clock.instant());
        assertThat(response.verified()).isTrue();
    }

    @Test
    void verify_whenProviderReturnsNoNameAtBank_skipsTheNameCheckAndMarksVerified() {
        WorkerBankDetail detail = existingDetail("Ramesh Kumar");
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.of(detail));
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, null, "ref-4"));
        when(bankDetails.save(detail)).thenReturn(detail);

        WorkerBankDetailResponse response = service.verify(workerId);

        assertThat(detail.isVerified()).isTrue();
        assertThat(response.verified()).isTrue();
    }

    // ---- check (standalone, no workerId) -----------------------------------------------------

    @Test
    void check_neverTouchesTheWorkerOrBankDetailRepositories() {
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, "Ramesh Kumar", "ref-5"));

        BankAccountCheckResponse response = service.check("31234567890", "SBIN0001234", "Ramesh Kumar");

        assertThat(response.exists()).isTrue();
        assertThat(response.nameMatched()).isTrue();
        assertThat(response.nameAtBank()).isEqualTo("Ramesh Kumar");
        verify(workers, never()).existsById(any());
        verify(bankDetails, never()).findByWorkerId(any());
        verify(bankDetails, never()).save(any());
    }

    @Test
    void check_whenAccountDoesNotExist_reportsExistsFalseAndNeverThrows() {
        when(bankVerificationPort.verify("00000000000", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(false, null, "ref-6"));

        BankAccountCheckResponse response = service.check("00000000000", "SBIN0001234", "Ramesh Kumar");

        assertThat(response.exists()).isFalse();
        assertThat(response.nameMatched()).isFalse();
    }

    @Test
    void check_whenNameDoesNotMatch_reportsNameMatchedFalseWithoutThrowing() {
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, "Someone Else Entirely", "ref-7"));

        BankAccountCheckResponse response = service.check("31234567890", "SBIN0001234", "Ramesh Kumar");

        assertThat(response.exists()).isTrue();
        assertThat(response.nameMatched()).isFalse();
        assertThat(response.nameAtBank()).isEqualTo("Someone Else Entirely");
    }

    @Test
    void verify_delegatesItsMatchPolicyToCheck_consistentResultsForTheSameInputs() {
        // Not a mock-interaction test - proves verify() and check() reach the same exists/nameMatched
        // conclusion for identical inputs, now that verify() is implemented in terms of check().
        WorkerBankDetail detail = existingDetail("Ramesh Kumar");
        when(workers.existsById(workerId)).thenReturn(true);
        when(bankDetails.findByWorkerId(workerId)).thenReturn(Optional.of(detail));
        when(bankVerificationPort.verify("31234567890", "SBIN0001234"))
                .thenReturn(new BankVerificationResult(true, "Ramesh Kumar", "ref-8"));
        when(bankDetails.save(detail)).thenReturn(detail);

        WorkerBankDetailResponse verifyResult = service.verify(workerId);
        BankAccountCheckResponse checkResult = service.check("31234567890", "SBIN0001234", "Ramesh Kumar");

        assertThat(verifyResult.verified()).isTrue();
        assertThat(checkResult.exists()).isTrue();
        assertThat(checkResult.nameMatched()).isTrue();
    }
}
