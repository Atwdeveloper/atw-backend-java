package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.CompanyAddress;
import in.anytimework.atwbackend.company.domain.CompanyAddressRepository;
import in.anytimework.atwbackend.company.domain.CompanyRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * A company's addresses exposed as a standalone sub-resource (list/add/update/delete one at a time) -
 * kept as its own service rather than methods on {@link CompanyService}, mirroring
 * {@code worker.application.WorkerAddressService} being a separate sibling service to
 * {@code WorkerService}. This resolves the open question {@link CompanyService}'s own class Javadoc
 * leaves for "whoever builds the admin controller": a company's addresses are a genuinely
 * variable-length list (one registered office plus any number of branches), not {@code WorkerAddress}'s
 * fixed-2-rows shape, so full sub-resource CRUD - not a single fixed-shape endpoint per address type -
 * is the natural fit. Only {@link CompanyService#create} persists an initial address list; every
 * address change after that goes through this class.
 *
 * <p><strong>"Exactly one primary per company":</strong> enforced here as a best-effort
 * demote-then-promote swap (in {@link #add}/{@link #update}) or delete-then-promote swap (in
 * {@link #delete}), with the DB's own partial unique index ({@code uq_company_address_primary},
 * {@code V37__company.sql}, redefined by {@code V38__company_address_soft_delete.sql} to also exclude
 * soft-deleted rows) as the final backstop against a concurrent request racing this one - the same
 * accepted check-then-write race tolerance {@code CompanyService.create}'s own Javadoc documents for
 * GST/PAN uniqueness. Demoting/soft-deleting the previous primary is flushed immediately
 * ({@code saveAndFlush}) before the new/promoted row is written, so the two statements can never
 * collide against the same unique index within one transaction. {@link #add} still enforces no
 * stronger invariant than that - a company may transiently have zero primary addresses immediately
 * after creation, before any address has been added yet. {@link #update} and {@link #delete} both
 * enforce the stronger invariant once at least one address exists: {@link #update} rejects any attempt
 * to unset the current primary address unless a different one is being promoted in the same call (see
 * that method's own Javadoc), and {@link #delete} either blocks deleting a company's last address
 * outright or auto-promotes a replacement when deleting the current primary - see each method's own
 * Javadoc.</p>
 */
@Service
public class CompanyAddressService {

    private final CompanyRepository companies;
    private final CompanyAddressRepository addresses;
    private final GeographyLookupService geography;
    private final Clock clock;

    public CompanyAddressService(CompanyRepository companies, CompanyAddressRepository addresses,
                                 GeographyLookupService geography, Clock clock) {
        this.companies = companies;
        this.addresses = addresses;
        this.geography = geography;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CompanyAddressView> list(UUID companyId) {
        requireCompanyExists(companyId);
        return addresses.findByCompanyId(companyId).stream().map(CompanyAddressService::toView).toList();
    }

    @Transactional
    public CompanyAddressView add(UUID companyId, CompanyAddressInput input) {
        requireCompanyExists(companyId);
        validateLocation(input.stateId(), input.cityId());
        if (input.primary()) {
            demoteCurrentPrimary(companyId, null);
        }
        CompanyAddress address = new CompanyAddress(companyId, input.addressType(), input.primary());
        address.update(input.line1(), input.line2(), input.stateId(), input.cityId(), input.pincode());
        return toView(addresses.save(address));
    }

    /**
     * Updates one of a company's addresses. If {@code command.primary()} is {@code true} and a
     * different address currently holds primary, that address is demoted first (see
     * {@link #demoteCurrentPrimary}).
     *
     * <p>Rejects, via {@link BusinessRuleException}, any attempt to flip the CURRENT primary address to
     * non-primary with no replacement being promoted in the same call - without this guard, a bare
     * {@code {"primary": false}} update on the primary address would leave the company with zero
     * primary addresses indefinitely, with nothing to self-heal that state (unlike {@link #delete},
     * which auto-promotes a replacement when it deletes the primary). To actually change which address
     * is primary, set {@code primary=true} on the new one instead; to remove the primary address
     * entirely, delete it (which does auto-promote a replacement).</p>
     */
    @Transactional
    public CompanyAddressView update(UUID companyId, UUID addressId, UpdateCompanyAddressCommand command) {
        requireCompanyExists(companyId);
        CompanyAddress address = findOwnedOrThrow(companyId, addressId);
        validateLocation(command.stateId(), command.cityId());
        if (address.isPrimary() && !command.primary()) {
            throw new BusinessRuleException(
                    "Cannot unset this company's primary address directly; "
                            + "make a different address primary first, or delete this one.");
        }
        if (command.primary() && !address.isPrimary()) {
            demoteCurrentPrimary(companyId, addressId);
        }
        address.update(command.line1(), command.line2(), command.stateId(), command.cityId(), command.pincode());
        address.setPrimary(command.primary());
        return toView(addresses.save(address));
    }

    /**
     * Soft-deletes one of a company's addresses ({@link CompanyAddress#markDeleted} - never a real SQL
     * DELETE via {@code addresses.delete(...)}, same mechanism as {@code Company}/{@code CompanyUser}).
     * {@code @SQLRestriction} then makes the deleted row invisible to every other method in this class
     * automatically.
     *
     * <p>A company must always have at least one address: if {@code addressId} is the only
     * (non-deleted) address this company has left, the delete is blocked outright with a
     * {@link BusinessRuleException} and nothing is written. Otherwise, if it is currently the primary
     * address, the next-oldest surviving address for this company is auto-promoted to primary in this
     * same transaction, flushed immediately before the promotion so the requery below only ever sees
     * the now-deleted row excluded (same flush-before-requery reasoning as
     * {@link #demoteCurrentPrimary}) - a company with at least one address must always have exactly
     * one marked primary. A non-primary address is simply soft-deleted, no promotion needed.</p>
     */
    @Transactional
    public void delete(UUID companyId, UUID addressId) {
        requireCompanyExists(companyId);
        CompanyAddress address = findOwnedOrThrow(companyId, addressId);
        if (addresses.findByCompanyId(companyId).size() <= 1) {
            throw new BusinessRuleException(
                    "A company must always have at least one address; cannot delete its only remaining address.");
        }

        boolean wasPrimary = address.isPrimary();
        address.markDeleted(clock.instant());
        addresses.saveAndFlush(address);

        if (wasPrimary) {
            addresses.findFirstByCompanyIdOrderByCreatedAtAsc(companyId).ifPresent(next -> {
                next.setPrimary(true);
                addresses.save(next);
            });
        }
    }

    /** Demotes this company's current primary address (if any, and if it isn't {@code keepAddressId} itself), flushed immediately - see class Javadoc. */
    private void demoteCurrentPrimary(UUID companyId, UUID keepAddressId) {
        addresses.findByCompanyIdAndPrimaryTrue(companyId)
                .filter(current -> !current.getId().equals(keepAddressId))
                .ifPresent(current -> {
                    current.setPrimary(false);
                    addresses.saveAndFlush(current);
                });
    }

    private void validateLocation(UUID stateId, UUID cityId) {
        if (stateId != null && !geography.stateExists(stateId)) {
            throw new ValidationException("Unknown state.");
        }
        if (cityId != null && !geography.cityExists(cityId)) {
            throw new ValidationException("Unknown city.");
        }
    }

    private void requireCompanyExists(UUID companyId) {
        if (!companies.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found");
        }
    }

    /**
     * Resolves the address and confirms it actually belongs to {@code companyId} - never trusts the
     * path's {@code addressId} alone, so one company's address can never be read/edited/deleted
     * through another company's {@code {id}} segment (the exact IDOR shape this codebase's security
     * review process exists to catch).
     */
    private CompanyAddress findOwnedOrThrow(UUID companyId, UUID addressId) {
        CompanyAddress address = addresses.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getCompanyId().equals(companyId)) {
            throw new ResourceNotFoundException("Address not found");
        }
        return address;
    }

    private static CompanyAddressView toView(CompanyAddress address) {
        return new CompanyAddressView(address.getId(), address.getAddressType(), address.isPrimary(),
                address.getAddress().getLine1(), address.getAddress().getLine2(),
                address.getAddress().getStateId(), address.getAddress().getCityId(), address.getAddress().getPincode());
    }
}
