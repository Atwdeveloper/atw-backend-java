package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserAddressDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAddressRequest;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserAddress;
import in.anytimework.atwbackend.identity.domain.AdminUserAddressRepository;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Ordinary demographic address data for a staff member - no masking needed, unlike KYC/bank detail. */
@Service
public class AdminStaffAddressService {

    private final AdminUserRepository adminUsers;
    private final AdminUserAddressRepository addresses;
    private final GeographyLookupService geography;
    private final AdminAuditService audit;
    private final CurrentAdmin currentAdmin;

    public AdminStaffAddressService(AdminUserRepository adminUsers, AdminUserAddressRepository addresses,
                                     GeographyLookupService geography, AdminAuditService audit, CurrentAdmin currentAdmin) {
        this.adminUsers = adminUsers;
        this.addresses = addresses;
        this.geography = geography;
        this.audit = audit;
        this.currentAdmin = currentAdmin;
    }

    @Transactional(readOnly = true)
    public AdminUserAddressDto get(UUID staffId) {
        requireStaffExists(staffId);
        return addresses.findByAdminUserId(staffId).map(this::toDto)
                .orElse(new AdminUserAddressDto(null, null, null, null, null, null, null, null));
    }

    @Transactional
    public AdminUserAddressDto update(UUID staffId, UpdateAddressRequest request, String ip, String userAgent) {
        AdminUser admin = requireStaffExists(staffId);
        if (admin.isSuperAdmin()) {
            throw new BusinessRuleException("Super-admin accounts cannot be modified through this endpoint.");
        }
        if (request.stateId() != null && !geography.stateExists(request.stateId())) {
            throw new ValidationException("Unknown state.");
        }
        if (request.cityId() != null && !geography.cityExists(request.cityId())) {
            throw new ValidationException("Unknown city.");
        }

        AdminUserAddress address = addresses.findByAdminUserId(staffId).orElseGet(() -> new AdminUserAddress(staffId));
        address.update(request.line1(), request.line2(), request.stateId(), request.cityId(), request.pincode());
        address = addresses.save(address);
        audit.record(currentAdmin.id().orElse(null), "ADMIN_STAFF_ADDRESS_UPDATED", staffId.toString(), ip, userAgent);
        return toDto(address);
    }

    private AdminUser requireStaffExists(UUID staffId) {
        return adminUsers.findById(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    private AdminUserAddressDto toDto(AdminUserAddress entity) {
        var address = entity.getAddress();
        return new AdminUserAddressDto(address.getLine1(), address.getLine2(), address.getStateId(),
                address.getCityId(), address.getPincode(), address.getLatitude(), address.getLongitude(),
                address.getFormattedAddress());
    }
}
