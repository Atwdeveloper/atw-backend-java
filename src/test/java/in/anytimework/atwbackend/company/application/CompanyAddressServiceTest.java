package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyAddress;
import in.anytimework.atwbackend.company.domain.CompanyAddressRepository;
import in.anytimework.atwbackend.company.domain.CompanyAddressType;
import in.anytimework.atwbackend.company.domain.CompanyRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no Spring context, no DB) for {@link CompanyAddressService}, mirroring
 * {@code CompanyServiceStatusGuardTest}'s Mockito-only style.
 *
 * <p>Regression coverage for the fix that made {@link CompanyAddressService#update} and
 * {@link CompanyAddressService#delete} call {@code requireCompanyExists} first, same as
 * {@link CompanyAddressService#list}/{@link CompanyAddressService#add} already did. Before that fix,
 * both methods resolved the address only via {@code findOwnedOrThrow(companyId, addressId)} - which
 * checks the address row's {@code companyId} FK but never that the company itself still exists - so a
 * soft-deleted company's addresses could still be mutated/deleted through its sub-resource endpoints
 * even though every other company endpoint correctly 404s once {@code Company.deletedAt} is set
 * ({@code @SQLRestriction}).</p>
 *
 * <p>Also covers {@link CompanyAddressService#delete}'s soft-delete semantics and its "a company must
 * always have at least one address, and exactly one primary once it has any" rules - see that method's
 * own Javadoc. The real DB enforcement of the redefined {@code uq_company_address_primary} partial
 * unique index (including the primary-swap-on-update path these mock-only tests cannot exercise) is
 * covered separately by {@code AdminCompanyControllerIntegrationTest} against a real Postgres.</p>
 */
class CompanyAddressServiceTest {

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyAddressRepository addresses = mock(CompanyAddressRepository.class);
    private final GeographyLookupService geography = mock(GeographyLookupService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T06:00:00Z"), ZoneOffset.UTC);

    private final UUID companyId = UUID.randomUUID();

    private CompanyAddressService service() {
        return new CompanyAddressService(companies, addresses, geography, clock);
    }

    private CompanyAddress existingAddress(UUID ownerCompanyId) {
        CompanyAddress address = new CompanyAddress(ownerCompanyId, CompanyAddressType.REGISTERED_OFFICE, true);
        address.update("Line 1", null, null, null, null);
        return address;
    }

    private UpdateCompanyAddressCommand updateCommand() {
        return new UpdateCompanyAddressCommand(true, "New Line 1", null, null, null, null);
    }

    // ---- regression: a soft-deleted/non-existent company must 404, even if the address row survives ----

    @Test
    void update_whenCompanyDoesNotExist_throwsNotFound_andNeverTouchesTheAddressRow() {
        when(companies.existsById(companyId)).thenReturn(false);

        assertThatThrownBy(() -> service().update(companyId, UUID.randomUUID(), updateCommand()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Company not found");

        verify(addresses, never()).findById(any());
        verify(addresses, never()).save(any());
    }

    @Test
    void delete_whenCompanyDoesNotExist_throwsNotFound_andNeverTouchesTheAddressRow() {
        when(companies.existsById(companyId)).thenReturn(false);

        assertThatThrownBy(() -> service().delete(companyId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Company not found");

        verify(addresses, never()).findById(any());
        verify(addresses, never()).delete(any());
    }

    // ---- sanity: the fix must not break the normal, company-exists path ----

    @Test
    void update_whenCompanyExistsAndAddressIsOwnedByIt_succeeds() {
        // Note: address.getId() is only assigned by Hibernate's @UuidGenerator inside a real
        // persistence context, so a freshly-constructed entity here has a null id - the
        // repository lookup key is a separate, explicit UUID rather than address.getId().
        UUID addressId = UUID.randomUUID();
        when(companies.existsById(companyId)).thenReturn(true);
        CompanyAddress address = existingAddress(companyId);
        when(addresses.findById(addressId)).thenReturn(Optional.of(address));
        when(addresses.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addresses.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service().update(companyId, addressId, updateCommand())).doesNotThrowAnyException();

        assertThat(address.getAddress().getLine1()).isEqualTo("New Line 1");
    }

    // ---- update(): cannot unset the current primary address with no replacement promoted ----

    @Test
    void update_cannotUnsetThePrimaryAddressDirectly_andNothingIsWritten() {
        UUID addressId = UUID.randomUUID();
        CompanyAddress address = existingAddress(companyId); // starts primary = true
        when(companies.existsById(companyId)).thenReturn(true);
        when(addresses.findById(addressId)).thenReturn(Optional.of(address));
        UpdateCompanyAddressCommand demoteOnly = new UpdateCompanyAddressCommand(false, "New Line 1", null, null, null, null);

        assertThatThrownBy(() -> service().update(companyId, addressId, demoteOnly))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot unset");

        assertThat(address.isPrimary()).isTrue(); // unchanged
        assertThat(address.getAddress().getLine1()).isNotEqualTo("New Line 1"); // nothing applied either
        verify(addresses, never()).save(any());
        verify(addresses, never()).saveAndFlush(any());
    }

    @Test
    void update_nonPrimaryAddressStayingNonPrimary_isNotBlocked() {
        // The guard is specifically "currently primary -> false", not "any primary=false value" -
        // editing an already-non-primary address (leaving it non-primary) must still work normally.
        UUID addressId = UUID.randomUUID();
        CompanyAddress address = new CompanyAddress(companyId, CompanyAddressType.BRANCH, false);
        address.update("Old Line 1", null, null, null, null);
        when(companies.existsById(companyId)).thenReturn(true);
        when(addresses.findById(addressId)).thenReturn(Optional.of(address));
        when(addresses.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateCompanyAddressCommand staysNonPrimary = new UpdateCompanyAddressCommand(false, "New Line 1", null, null, null, null);

        assertThatCode(() -> service().update(companyId, addressId, staysNonPrimary)).doesNotThrowAnyException();

        assertThat(address.isPrimary()).isFalse();
        assertThat(address.getAddress().getLine1()).isEqualTo("New Line 1");
    }

    // ---- delete(): soft-delete semantics + "at least one address" / "exactly one primary" rules ----

    @Test
    void delete_nonPrimaryAddress_softDeletesWithoutPromotingAnything() {
        // The row is never passed to addresses.delete(...) - it is soft-deleted (deletedAt set) so it
        // still exists, just filtered out of every normal query from here on via @SQLRestriction.
        UUID addressId = UUID.randomUUID();
        CompanyAddress primary = existingAddress(companyId);
        CompanyAddress nonPrimary = new CompanyAddress(companyId, CompanyAddressType.BRANCH, false);
        nonPrimary.update("Branch Line 1", null, null, null, null);

        when(companies.existsById(companyId)).thenReturn(true);
        when(addresses.findById(addressId)).thenReturn(Optional.of(nonPrimary));
        when(addresses.findByCompanyId(companyId)).thenReturn(List.of(primary, nonPrimary));
        when(addresses.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(companyId, addressId);

        assertThat(nonPrimary.getDeletedAt()).isNotNull();
        verify(addresses).saveAndFlush(nonPrimary);
        verify(addresses, never()).delete(any());
        verify(addresses, never()).findFirstByCompanyIdOrderByCreatedAtAsc(any());
        verify(addresses, never()).save(primary);
    }

    @Test
    void delete_primaryAddress_whenAnotherRemains_promotesNextOldestToPrimary() {
        UUID addressId = UUID.randomUUID();
        CompanyAddress primary = existingAddress(companyId); // the one being deleted
        CompanyAddress other = new CompanyAddress(companyId, CompanyAddressType.BRANCH, false);
        other.update("Branch Line 1", null, null, null, null);

        when(companies.existsById(companyId)).thenReturn(true);
        when(addresses.findById(addressId)).thenReturn(Optional.of(primary));
        when(addresses.findByCompanyId(companyId)).thenReturn(List.of(primary, other));
        when(addresses.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(addresses.findFirstByCompanyIdOrderByCreatedAtAsc(companyId)).thenReturn(Optional.of(other));
        when(addresses.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().delete(companyId, addressId);

        assertThat(primary.getDeletedAt()).isNotNull();
        assertThat(other.isPrimary()).isTrue();
        verify(addresses).saveAndFlush(primary);
        verify(addresses).save(other);
        verify(addresses, never()).delete(any());
    }

    @Test
    void delete_onlyRemainingAddress_isBlocked_andNothingIsWritten() {
        UUID addressId = UUID.randomUUID();
        CompanyAddress address = existingAddress(companyId);

        when(companies.existsById(companyId)).thenReturn(true);
        when(addresses.findById(addressId)).thenReturn(Optional.of(address));
        when(addresses.findByCompanyId(companyId)).thenReturn(List.of(address));

        assertThatThrownBy(() -> service().delete(companyId, addressId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one address");

        assertThat(address.getDeletedAt()).isNull();
        verify(addresses, never()).saveAndFlush(any());
        verify(addresses, never()).save(any());
        verify(addresses, never()).delete(any());
    }
}
