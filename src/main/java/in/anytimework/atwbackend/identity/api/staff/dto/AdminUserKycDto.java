package in.anytimework.atwbackend.identity.api.staff.dto;

/** Values are masked (e.g. "********1234") unless the caller holds admin_staff.view_kyc - see AdminStaffKycService. */
public record AdminUserKycDto(
        String aadhaarNumber,
        String panNumber,
        String uanNumber,
        String pfNumber,
        String esicNumber
) {
}
