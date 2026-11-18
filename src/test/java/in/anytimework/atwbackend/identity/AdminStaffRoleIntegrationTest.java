package in.anytimework.atwbackend.identity;

import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleResponse;
import in.anytimework.atwbackend.identity.api.role.dto.UpdateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserAddressResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserBankDetailResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserKycResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserProfileResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminRoleRef;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAddressRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateBankDetailRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateKycRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateProfileRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffRequest;
import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.AdminRoleService;
import in.anytimework.atwbackend.identity.application.AdminStaffAddressService;
import in.anytimework.atwbackend.identity.application.AdminStaffBankDetailService;
import in.anytimework.atwbackend.identity.application.AdminStaffKycService;
import in.anytimework.atwbackend.identity.application.AdminStaffProfileService;
import in.anytimework.atwbackend.identity.application.AdminStaffSearchCriteria;
import in.anytimework.atwbackend.identity.application.AdminStaffService;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminStatus;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.Gender;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.masterdata.domain.City;
import in.anytimework.atwbackend.masterdata.domain.CityRepository;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.notification.CapturingEmailSender;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for the Staff & Roles administration API against the real local Postgres:
 * role CRUD + its guards, staff creation -> email invite -> accept-invite -> login resolving the
 * assigned role's permissions, status changes revoking sessions, the self/super-admin guards, and
 * the privilege-escalation guard (can't assign a role more powerful than your own).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AdminStaffRoleIntegrationTest.EmailTestConfig.class)
class AdminStaffRoleIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern TOKEN_PARAM = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private AdminRoleService roleService;
    @Autowired
    private AdminStaffService staffService;
    @Autowired
    private AdminStaffProfileService staffProfileService;
    @Autowired
    private AdminStaffAddressService staffAddressService;
    @Autowired
    private AdminStaffKycService staffKycService;
    @Autowired
    private AdminStaffBankDetailService staffBankDetailService;
    @Autowired
    private AuthService authService;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private AdminRoleRepository roles;
    @Autowired
    private StateRepository states;
    @Autowired
    private CityRepository cities;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CapturingEmailSender emailSender;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String extractToken(String html) {
        Matcher m = TOKEN_PARAM.matcher(html);
        assertThat(m.find()).as("token present in email body").isTrue();
        return m.group(1);
    }

    private static String extractOtp(String html) {
        Matcher m = SIX_DIGITS.matcher(html);
        assertThat(m.find()).as("OTP present in email body").isTrue();
        return m.group(1);
    }

    /**
     * Runs {@code action} with {@code adminId} as the authenticated principal, exactly as a real
     * request would see it: builds a token carrying the same claims AccessTokenService.issue()
     * would (sub, super_admin, authorities from the admin's actual current roles), then runs it
     * through the real JwtAuthenticationConverter bean - not a hand-rolled authorities list - so
     * PermissionChecker/@PreAuthorize behave identically to production.
     */
    private <T> T actingAs(UUID adminId, Supplier<T> action) {
        AdminUser admin = adminUsers.findById(adminId).orElseThrow();
        List<String> authorityCodes = admin.isSuperAdmin()
                ? List.of("SUPER_ADMIN")
                : adminUsers.findPermissionCodes(adminId);
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("sub", adminId.toString())
                .claim("super_admin", admin.isSuperAdmin())
                .claim("authorities", authorityCodes)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        Authentication authentication = jwtAuthenticationConverter.convert(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void actingAs(UUID adminId, Runnable action) {
        actingAs(adminId, () -> {
            action.run();
            return null;
        });
    }

    /** A throwaway super-admin, purely so test setup can assign roles without tripping the privilege-escalation guard. */
    private UUID createSuperAdminActor() {
        AdminUser superAdmin = adminUsers.save(new AdminUser("actor-" + unique(), "actor-" + unique() + "@example.com",
                passwordEncoder.encode("Whatever@123"), "Setup Actor", null, true, false));
        return superAdmin.getId();
    }

    private AdminStaffResponse createStaffAsSuperAdmin(String fullName, List<UUID> roleIds) {
        UUID setupActor = createSuperAdminActor();
        return actingAs(setupActor, () -> staffService.create(
                new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com", fullName, null, roleIds),
                "127.0.0.1", "JUnit"));
    }

    /** Role creation with a non-empty permissionCodes list also trips PermissionEscalationGuard without an actor. */
    private AdminRoleResponse createRoleAsSuperAdmin(String name, String description, List<String> permissionCodes) {
        UUID setupActor = createSuperAdminActor();
        return actingAs(setupActor, () -> roleService.create(
                new CreateAdminRoleRequest(name, description, permissionCodes), "127.0.0.1", "JUnit"));
    }

    @Test
    void fullLifecycle_createRoleThenStaff_acceptInvite_loginResolvesAssignedPermissions() {
        AdminRoleResponse role = createRoleAsSuperAdmin("QA Tester " + unique(), "d",
                List.of("department.view", "workers.view"));

        AdminStaffResponse staff = createStaffAsSuperAdmin("New Staff", List.of(role.id()));
        assertThat(staff.status()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(staff.roles().stream().map(AdminRoleRef::name).toList()).containsExactly(role.name());

        String inviteToken = extractToken(emailSender.last().htmlBody());
        authService.acceptInvite(inviteToken, "BrandNew@12345", "127.0.0.1", "JUnit");

        LoginResult login = authService.login(staff.email(), "BrandNew@12345", "127.0.0.1", "JUnit");
        assertThat(login.otpRequired()).isTrue();
        String otp = extractOtp(emailSender.last().htmlBody());
        AuthTokens tokens = authService.verifyOtp(login.challengeId(), otp, "127.0.0.1", "JUnit");

        assertThat(tokens.admin().authorities()).containsExactlyInAnyOrder("department.view", "workers.view");
        assertThat(tokens.admin().mustChangePassword()).isFalse();
    }

    @Test
    void createRole_withUnknownPermissionCode_isRejected() {
        assertThatThrownBy(() -> roleService.create(new CreateAdminRoleRequest("Bad Role " + unique(), null,
                List.of("not.a.real.code")), "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createRole_withDuplicateName_isRejected() {
        String name = "Dup Role " + unique();
        roleService.create(new CreateAdminRoleRequest(name, null, List.of()), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> roleService.create(new CreateAdminRoleRequest(name, null, List.of()), "127.0.0.1", "JUnit"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteRole_thatIsSystemRole_isRejected() {
        AdminRole systemRole = roles.save(new AdminRole("System Role " + unique(), "protected", true));

        assertThatThrownBy(() -> roleService.delete(systemRole.getId(), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updateRole_thatIsSystemRole_isRejected() {
        AdminRole systemRole = roles.save(new AdminRole("System Role " + unique(), "protected", true));

        assertThatThrownBy(() -> roleService.update(systemRole.getId(),
                new UpdateAdminRoleRequest("New Name", null, List.of()), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deleteRole_thatIsAssignedToStaff_isRejected() {
        AdminRoleResponse role = roleService.create(new CreateAdminRoleRequest("Assigned Role " + unique(), null, List.of()),
                "127.0.0.1", "JUnit");
        createStaffAsSuperAdmin("Staffer", List.of(role.id()));

        assertThatThrownBy(() -> roleService.delete(role.id(), "127.0.0.1", "JUnit"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createStaff_withDuplicateEmail_isRejected() {
        String email = "staff-" + unique() + "@example.com";
        staffService.create(new CreateAdminStaffRequest(null, email, "First", null, List.of()), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> staffService.create(
                new CreateAdminStaffRequest(null, email, "Second", null, List.of()), "127.0.0.1", "JUnit"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createStaff_withEmailDifferingOnlyByCase_isRejectedAndStorageIsNormalized() {
        String local = "case-" + unique();
        staffService.create(new CreateAdminStaffRequest(null, local.toUpperCase() + "@Example.com", "First", null,
                List.of()), "127.0.0.1", "JUnit");

        assertThatThrownBy(() -> staffService.create(new CreateAdminStaffRequest(null,
                local.toLowerCase() + "@example.com", "Second", null, List.of()), "127.0.0.1", "JUnit"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createStaff_withUnknownRoleId_isRejected() {
        assertThatThrownBy(() -> staffService.create(new CreateAdminStaffRequest(null,
                "staff-" + unique() + "@example.com", "Someone", null, List.of(UUID.randomUUID())),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void disablingStaff_revokesTheirActiveSessions() {
        AdminStaffResponse staff = staffService.create(new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com",
                "To Disable", null, List.of()), "127.0.0.1", "JUnit");
        String inviteToken = extractToken(emailSender.last().htmlBody());
        authService.acceptInvite(inviteToken, "BrandNew@12345", "127.0.0.1", "JUnit");
        AuthTokens tokens = loginFully(staff);

        staffService.updateStatus(staff.id(), AdminStatus.DISABLED, "127.0.0.1", "JUnit");

        assertThat(adminUsers.findById(staff.id()).orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void staff_cannotDisableTheirOwnAccount() {
        AdminStaffResponse staff = staffService.create(new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com",
                "Self", null, List.of()), "127.0.0.1", "JUnit");

        actingAs(staff.id(), () -> assertThatThrownBy(() -> staffService.updateStatus(
                staff.id(), AdminStatus.DISABLED, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void superAdminAccounts_cannotBeDeletedThroughThisApi() {
        UUID superAdminId = createSuperAdminActor();

        assertThatThrownBy(() -> staffService.delete(superAdminId, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void superAdminAccounts_cannotBeUpdatedThroughThisApi() {
        UUID superAdminId = createSuperAdminActor();

        assertThatThrownBy(() -> staffService.update(superAdminId,
                new UpdateAdminStaffRequest(null, "New Name", null, List.of()), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void staffWithLimitedPermissions_cannotGrantAMorePrivilegedRoleOnCreate() {
        AdminRoleResponse limitedRole = createRoleAsSuperAdmin("Limited " + unique(), null, List.of("department.view"));
        AdminRoleResponse powerfulRole = createRoleAsSuperAdmin("Powerful " + unique(), null,
                List.of("department.view", "admin_staff.delete"));
        AdminStaffResponse limitedActor = createStaffAsSuperAdmin("Limited Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> staffService.create(new CreateAdminStaffRequest(null,
                "victim-" + unique() + "@example.com", "Victim", null, List.of(powerfulRole.id())),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void staffWithLimitedPermissions_cannotGrantAMorePrivilegedRoleOnUpdate() {
        AdminRoleResponse limitedRole = createRoleAsSuperAdmin("Limited " + unique(), null, List.of("department.view"));
        AdminRoleResponse powerfulRole = createRoleAsSuperAdmin("Powerful " + unique(), null,
                List.of("department.view", "admin_staff.delete"));
        AdminStaffResponse limitedActor = createStaffAsSuperAdmin("Limited Actor", List.of(limitedRole.id()));
        AdminStaffResponse victim = createStaffAsSuperAdmin("Victim", List.of());

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> staffService.update(victim.id(),
                new UpdateAdminStaffRequest(null, "Victim", null, List.of(powerfulRole.id())), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void staffProfile_updateThenGet_roundTrips() {
        AdminStaffResponse staff = createStaffAsSuperAdmin("Profile Target", List.of());

        AdminUserProfileResponse updated = staffProfileService.update(staff.id(),
                new UpdateProfileRequest(LocalDate.of(1995, 6, 15), Gender.FEMALE), "127.0.0.1", "JUnit");
        assertThat(updated.dateOfBirth()).isEqualTo(LocalDate.of(1995, 6, 15));

        AdminUserProfileResponse fetched = staffProfileService.get(staff.id());
        assertThat(fetched.gender()).isEqualTo(Gender.FEMALE);
    }

    private City createTestCity() {
        State rajasthan = states.findAll().stream()
                .filter(s -> s.getCode().equals("RJ")).findFirst().orElseThrow();
        return cities.save(new City(rajasthan.getId(), "Jaipur " + unique()));
    }

    @Test
    void staffAddress_updateThenGet_roundTrips() {
        AdminStaffResponse staff = createStaffAsSuperAdmin("Address Target", List.of());
        City jaipur = createTestCity();

        AdminUserAddressResponse updated = staffAddressService.update(staff.id(),
                new UpdateAddressRequest("221B Baker Street", "Near City Park", jaipur.getStateId(), jaipur.getId(), "302001"),
                "127.0.0.1", "JUnit");
        assertThat(updated.line1()).isEqualTo("221B Baker Street");
        assertThat(updated.cityId()).isEqualTo(jaipur.getId());

        AdminUserAddressResponse fetched = staffAddressService.get(staff.id());
        assertThat(fetched.line1()).isEqualTo("221B Baker Street");
        assertThat(fetched.line2()).isEqualTo("Near City Park");
        assertThat(fetched.stateId()).isEqualTo(jaipur.getStateId());
        assertThat(fetched.pincode()).isEqualTo("302001");
    }

    @Test
    void staffAddress_line1IsStoredAsPlainTextNotEncrypted() {
        // Addresses were encrypted at rest until 2026-11-13 (confirmed with the developer to remove
        // it - see Address's Javadoc); this bypasses Hibernate to read the raw stored column and
        // guards against that decision being silently reversed by a future @Convert re-added here.
        AdminStaffResponse target = createStaffAsSuperAdmin("Address Encryption Target", List.of());

        staffAddressService.update(target.id(),
                new UpdateAddressRequest("42 Residency Road", null, null, null, null), "127.0.0.1", "JUnit");

        String rawStored = jdbcTemplate.queryForObject(
                "select line1 from admin_user_address where admin_user_id = ?", String.class, target.id());
        assertThat(rawStored).isEqualTo("42 Residency Road");
    }

    @Test
    void updateAddress_withUnknownState_isRejected() {
        AdminStaffResponse target = createStaffAsSuperAdmin("Bad State Target", List.of());

        assertThatThrownBy(() -> staffAddressService.update(target.id(),
                new UpdateAddressRequest(null, null, UUID.randomUUID(), null, null), "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateAddress_onSuperAdminTarget_isRejected() {
        UUID superAdminId = createSuperAdminActor();

        assertThatThrownBy(() -> staffAddressService.update(superAdminId,
                new UpdateAddressRequest("Somewhere", null, null, null, null), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void staffKyc_isEncryptedAtRest_andMaskedWithoutViewKycPermission() {
        AdminStaffResponse target = createStaffAsSuperAdmin("KYC Target", List.of());
        UUID setupActor = createSuperAdminActor();
        actingAs(setupActor, () -> staffKycService.update(target.id(),
                new UpdateKycRequest("123456789012", "ABCDE1234F", null, null, null), "127.0.0.1", "JUnit"));

        // Encrypted at rest: read the TRUE column value via raw JDBC, bypassing JPA's
        // AttributeConverter (which would transparently decrypt it back to plaintext, same as the
        // service layer does - that's the point of it, but it means the repository/entity getter
        // can never be used to verify encryption actually happened).
        String rawStored = jdbcTemplate.queryForObject(
                "select aadhaar_number from admin_user_kyc where admin_user_id = ?", String.class, target.id());
        assertThat(rawStored).isNotEqualTo("123456789012").doesNotContain("123456789012");

        AdminRoleResponse viewOnly = createRoleAsSuperAdmin("View Only " + unique(), null, List.of("admin_staff.view"));
        AdminRoleResponse viewKyc = createRoleAsSuperAdmin("View KYC " + unique(), null,
                List.of("admin_staff.view", "admin_staff.view_kyc"));
        AdminStaffResponse viewOnlyActor = createStaffAsSuperAdmin("View Only Actor", List.of(viewOnly.id()));
        AdminStaffResponse viewKycActor = createStaffAsSuperAdmin("View KYC Actor", List.of(viewKyc.id()));

        AdminUserKycResponse masked = actingAs(viewOnlyActor.id(), () -> staffKycService.get(target.id()));
        assertThat(masked.aadhaarNumber()).isNotEqualTo("123456789012").endsWith("9012");
        assertThat(masked.panNumber()).isNotEqualTo("ABCDE1234F").endsWith("234F");

        AdminUserKycResponse unmasked = actingAs(viewKycActor.id(), () -> staffKycService.get(target.id()));
        assertThat(unmasked.aadhaarNumber()).isEqualTo("123456789012");
        assertThat(unmasked.panNumber()).isEqualTo("ABCDE1234F");
    }

    @Test
    void staffBankDetail_accountNumberIsEncryptedAndMaskedWithoutViewBankDetailsPermission() {
        AdminStaffResponse target = createStaffAsSuperAdmin("Bank Target", List.of());
        UUID setupActor = createSuperAdminActor();
        actingAs(setupActor, () -> staffBankDetailService.update(target.id(),
                new UpdateBankDetailRequest("Target Holder", "Test Bank", "000111222333", "TEST0001234", "MG Road"),
                "127.0.0.1", "JUnit"));

        String rawStored = jdbcTemplate.queryForObject(
                "select account_number from admin_user_bank_detail where admin_user_id = ?", String.class, target.id());
        assertThat(rawStored).isNotEqualTo("000111222333").doesNotContain("000111222333");

        AdminRoleResponse viewOnly = createRoleAsSuperAdmin("View Only Bank " + unique(), null, List.of("admin_staff.view"));
        AdminStaffResponse viewOnlyActor = createStaffAsSuperAdmin("View Only Bank Actor", List.of(viewOnly.id()));

        AdminUserBankDetailResponse masked = actingAs(viewOnlyActor.id(), () -> staffBankDetailService.get(target.id()));
        assertThat(masked.accountNumber()).isNotEqualTo("000111222333").endsWith("2333");
        // Non-sensitive fields are never masked.
        assertThat(masked.bankName()).isEqualTo("Test Bank");
        assertThat(masked.ifscCode()).isEqualTo("TEST0001234");
    }

    @Test
    void updateProfile_onSuperAdminTarget_isRejected() {
        UUID superAdminId = createSuperAdminActor();

        assertThatThrownBy(() -> staffProfileService.update(superAdminId,
                new UpdateProfileRequest(null, null), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void roleCreate_cannotGrantAPermissionBeyondTheActorsOwn() {
        AdminRoleResponse limitedRole = createRoleAsSuperAdmin("Limited RC " + unique(), null, List.of("department.view"));
        AdminStaffResponse limitedActor = createStaffAsSuperAdmin("Limited RC Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> roleService.create(
                new CreateAdminRoleRequest("Sneaky " + unique(), null, List.of("department.view", "admin_staff.delete")),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void roleUpdate_cannotGrantAPermissionBeyondTheActorsOwn() {
        // The attack this guards against: editing an ALREADY-ASSIGNED role's permissions after the
        // fact, rather than assigning a new, more-powerful role - AdminStaffService's own guard only
        // ever sees an assignment event, not a later mutation of a role someone already holds.
        AdminRoleResponse limitedRole = createRoleAsSuperAdmin("Limited RU " + unique(), null, List.of("department.view"));
        AdminRoleResponse targetRole = createRoleAsSuperAdmin("Target RU " + unique(), null, List.of("department.view"));
        AdminStaffResponse limitedActor = createStaffAsSuperAdmin("Limited RU Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> roleService.update(targetRole.id(),
                new UpdateAdminRoleRequest("Target RU", null, List.of("department.view", "admin_staff.delete")),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void changingStaffRoles_forcesReLogin_butAPlainProfileEditDoesNot() {
        AdminRoleResponse roleA = roleService.create(new CreateAdminRoleRequest("Role A " + unique(), null, List.of()),
                "127.0.0.1", "JUnit");
        AdminRoleResponse roleB = roleService.create(new CreateAdminRoleRequest("Role B " + unique(), null, List.of()),
                "127.0.0.1", "JUnit");
        AdminStaffResponse staff = createStaffAsSuperAdmin("Role Change Target", List.of(roleA.id()));
        String inviteToken = extractToken(emailSender.last().htmlBody());
        authService.acceptInvite(inviteToken, "BrandNew@12345", "127.0.0.1", "JUnit");
        AuthTokens tokens = loginFully(staff);

        // Same roles (roleA again), just a name change - must NOT force a re-login.
        staffService.update(staff.id(), new UpdateAdminStaffRequest(null, "Role Change Target Renamed", null,
                List.of(roleA.id())), "127.0.0.1", "JUnit");
        AuthTokens afterProfileEdit = authService.refresh(tokens.refreshToken(), "127.0.0.1", "JUnit");
        assertThat(afterProfileEdit.accessToken()).isNotBlank();

        // Different roles - must force a re-login: the refresh token just issued above dies too.
        staffService.update(staff.id(), new UpdateAdminStaffRequest(null, "Role Change Target Renamed", null,
                List.of(roleB.id())), "127.0.0.1", "JUnit");
        assertThatThrownBy(() -> authService.refresh(afterProfileEdit.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void auditLog_recordsActorEmailSnapshot_soAttributionSurvivesTheActorLaterBeingDeleted() {
        UUID actorId = createSuperAdminActor();
        String actorEmail = adminUsers.findById(actorId).orElseThrow().getEmail();

        actingAs(actorId, () -> roleService.create(
                new CreateAdminRoleRequest("Audited Role " + unique(), null, List.of()), "127.0.0.1", "JUnit"));

        String recordedEmail = jdbcTemplate.queryForObject(
                "select actor_email from admin_audit_log where actor_admin_id = ? order by created_at desc limit 1",
                String.class, actorId);
        assertThat(recordedEmail).isEqualTo(actorEmail);
    }

    @Test
    void createStaff_withEmptyStringUsername_normalizesToNullAndAllowsMultiple() {
        AdminStaffResponse first = staffService.create(new CreateAdminStaffRequest("", "staff-" + unique() + "@example.com",
                "Empty Username First", null, List.of()), "127.0.0.1", "JUnit");
        AdminStaffResponse second = staffService.create(new CreateAdminStaffRequest("", "staff-" + unique() + "@example.com",
                "Empty Username Second", null, List.of()), "127.0.0.1", "JUnit");

        assertThat(adminUsers.findById(first.id()).orElseThrow().getUsername()).isNull();
        assertThat(adminUsers.findById(second.id()).orElseThrow().getUsername()).isNull();
    }

    // ---- delete: soft-delete mechanism (2026-11-11 audit fix - was a hard delete) ------------------

    @Test
    void delete_softDeletesAdmin_disappearsFromGetAndLoginLookup() {
        UUID actorId = createSuperAdminActor();
        AdminStaffResponse target = createStaffAsSuperAdmin("Deletable Staffer", List.of());

        actingAs(actorId, () -> staffService.delete(target.id(), "127.0.0.1", "JUnit"));

        assertThatThrownBy(() -> staffService.get(target.id())).isInstanceOf(ResourceNotFoundException.class);
        // The security-critical property: AuthService's login lookup must not find a deleted admin.
        // (A literal null for the "username" half would make Spring Data translate it to "username IS
        // NULL", matching every other usernameless admin in this accumulating test DB - passing the
        // same value into both slots avoids that trap while still exercising the real OR-lookup.)
        assertThat(adminUsers.findByUsernameIgnoreCaseOrEmailIgnoreCase(target.email(), target.email())).isEmpty();
        assertThat(adminUsers.existsByEmailIgnoreCase(target.email())).isFalse();
    }

    @Test
    void delete_preservesRoleAssignmentRatherThanCascadingItAway() {
        UUID actorId = createSuperAdminActor();
        AdminRoleResponse role = createRoleAsSuperAdmin("Preserved Role " + unique(), null, List.of());
        AdminStaffResponse target = createStaffAsSuperAdmin("Staffer With Role", List.of(role.id()));

        actingAs(actorId, () -> staffService.delete(target.id(), "127.0.0.1", "JUnit"));

        // Queried directly (bypasses the now-@SQLRestriction-filtered AdminUser entity entirely) -
        // proves the admin_user_role row physically survives rather than cascading away.
        Integer roleAssignmentCount = jdbcTemplate.queryForObject(
                "select count(*) from admin_user_role where admin_user_id = ?", Integer.class, target.id());
        assertThat(roleAssignmentCount).isEqualTo(1);
    }

    @Test
    void delete_thenUsernameAndEmailBecomeAvailableForANewAdmin() {
        UUID actorId = createSuperAdminActor();
        String username = "reusable-" + unique();
        String email = "reusable-" + unique() + "@example.com";
        AdminStaffResponse original = actingAs(actorId, () -> staffService.create(
                new CreateAdminStaffRequest(username, email, "Original Holder", null, List.of()), "127.0.0.1", "JUnit"));

        actingAs(actorId, () -> staffService.delete(original.id(), "127.0.0.1", "JUnit"));

        assertThatCode(() -> actingAs(actorId, () -> staffService.create(
                new CreateAdminStaffRequest(username, email, "New Holder", null, List.of()), "127.0.0.1", "JUnit")))
                .doesNotThrowAnyException();
    }

    @Test
    void delete_targetingASuperAdmin_isRejected() {
        UUID actorId = createSuperAdminActor();
        UUID superAdminTarget = createSuperAdminActor();

        actingAs(actorId, () -> assertThatThrownBy(() -> staffService.delete(superAdminTarget, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void delete_targetingYourself_isRejected() {
        AdminStaffResponse self = createStaffAsSuperAdmin("Self Deleter", List.of());

        actingAs(self.id(), () -> assertThatThrownBy(() -> staffService.delete(self.id(), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    // ---- staff list: filters, search, sort ------------------------------------------------------

    @Test
    void staffList_statusFilterIsMultiselect_matchesAnySelectedValue() {
        String prefix = "MultiStatus" + unique();
        AdminStaffResponse active = createStaffAsSuperAdmin(prefix + " Active", List.of());
        AdminStaffResponse disabled = createStaffAsSuperAdmin(prefix + " Disabled", List.of());
        UUID actorId = createSuperAdminActor();
        actingAs(actorId, () -> staffService.updateStatus(disabled.id(), AdminStatus.DISABLED, "127.0.0.1", "JUnit"));

        PageResponse<AdminStaffResponse> result = staffService.list(
                new AdminStaffSearchCriteria(Set.of(AdminStatus.ACTIVE, AdminStatus.DISABLED), null, null, prefix, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(AdminStaffResponse::id)
                .containsExactlyInAnyOrder(active.id(), disabled.id());
    }

    @Test
    void staffList_statusFilter_excludesNonMatchingStatus() {
        String prefix = "StatusOnly" + unique();
        AdminStaffResponse active = createStaffAsSuperAdmin(prefix + " Active", List.of());
        AdminStaffResponse disabled = createStaffAsSuperAdmin(prefix + " Disabled", List.of());
        UUID actorId = createSuperAdminActor();
        actingAs(actorId, () -> staffService.updateStatus(disabled.id(), AdminStatus.DISABLED, "127.0.0.1", "JUnit"));

        PageResponse<AdminStaffResponse> disabledOnly = staffService.list(
                new AdminStaffSearchCriteria(Set.of(AdminStatus.DISABLED), null, null, prefix, null, null),
                PageRequest.of(0, 50));

        assertThat(disabledOnly.content()).extracting(AdminStaffResponse::id).containsExactly(disabled.id());
        assertThat(disabledOnly.content()).extracting(AdminStaffResponse::id).doesNotContain(active.id());
    }

    /**
     * The critical regression this guards against: filtering by a {@code Set} of role ids via a
     * naive {@code root.join("roles")} would duplicate a staff member's row once per matching role
     * they hold, inflating {@code totalElements} and returning the same id twice. {@code roleA}/
     * {@code roleB} are freshly created in this test, so they isolate this assertion from the shared
     * test database's accumulated rows without needing a search term too.
     */
    @Test
    void staffList_roleFilterIsMultiselect_doesNotDuplicateAStaffMemberHoldingBothSelectedRoles() {
        AdminRoleResponse roleA = createRoleAsSuperAdmin("RoleA " + unique(), "d", List.of());
        AdminRoleResponse roleB = createRoleAsSuperAdmin("RoleB " + unique(), "d", List.of());
        AdminStaffResponse both = createStaffAsSuperAdmin("Both Roles " + unique(), List.of(roleA.id(), roleB.id()));
        AdminStaffResponse onlyA = createStaffAsSuperAdmin("Only RoleA " + unique(), List.of(roleA.id()));
        AdminStaffResponse neither = createStaffAsSuperAdmin("Neither Role " + unique(), List.of());

        PageResponse<AdminStaffResponse> result = staffService.list(
                new AdminStaffSearchCriteria(null, Set.of(roleA.id(), roleB.id()), null, null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(AdminStaffResponse::id)
                .containsExactlyInAnyOrder(both.id(), onlyA.id());
        assertThat(result.content()).extracting(AdminStaffResponse::id).doesNotContain(neither.id());
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void staffList_searchMatchesPartialNameAndPartialEmail() {
        String marker = "Findstaff" + unique();
        AdminStaffResponse staff = createStaffAsSuperAdmin(marker + " Person", List.of());

        PageResponse<AdminStaffResponse> byName = staffService.list(
                new AdminStaffSearchCriteria(null, null, null, marker, null, null), PageRequest.of(0, 20));
        assertThat(byName.content()).extracting(AdminStaffResponse::id).contains(staff.id());

        PageResponse<AdminStaffResponse> byEmail = staffService.list(
                new AdminStaffSearchCriteria(null, null, null, staff.email().substring(0, 12), null, null), PageRequest.of(0, 20));
        assertThat(byEmail.content()).extracting(AdminStaffResponse::id).contains(staff.id());
    }

    @Test
    void staffList_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> staffService.list(
                new AdminStaffSearchCriteria(null, null, null, null, null, null),
                PageRequest.of(0, 10, Sort.by("passwordHash"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void staffList_stateAndCityFiltersMatchAStaffMembersAddress() {
        City jaipur = createTestCity();
        AdminStaffResponse inJaipur = createStaffAsSuperAdmin("Jaipur Staff " + unique(), List.of());
        AdminStaffResponse noAddress = createStaffAsSuperAdmin("No Address Staff " + unique(), List.of());
        staffAddressService.update(inJaipur.id(),
                new UpdateAddressRequest("1 MG Road", null, jaipur.getStateId(), jaipur.getId(), "302001"),
                "127.0.0.1", "JUnit");

        PageResponse<AdminStaffResponse> byCity = staffService.list(
                new AdminStaffSearchCriteria(null, null, null, null, null, Set.of(jaipur.getId())),
                PageRequest.of(0, 50));
        assertThat(byCity.content()).extracting(AdminStaffResponse::id).contains(inJaipur.id());
        assertThat(byCity.content()).extracting(AdminStaffResponse::id).doesNotContain(noAddress.id());

        // A brand-new State (not the shared "Rajasthan" fixture createTestCity() reuses) keeps this
        // assertion isolated from every other test's accumulated rows in the shared test database -
        // same reasoning as staffList_roleFilterIsMultiselect's freshly created roleA/roleB.
        State freshState = states.save(new State("StateFilter " + unique(), unique().toUpperCase()));
        City cityInFreshState = cities.save(new City(freshState.getId(), "City " + unique()));
        AdminStaffResponse inFreshState = createStaffAsSuperAdmin("State Filter Staff " + unique(), List.of());
        staffAddressService.update(inFreshState.id(),
                new UpdateAddressRequest("2 Park Street", null, freshState.getId(), cityInFreshState.getId(), "400001"),
                "127.0.0.1", "JUnit");

        PageResponse<AdminStaffResponse> byState = staffService.list(
                new AdminStaffSearchCriteria(null, null, null, null, Set.of(freshState.getId()), null),
                PageRequest.of(0, 50));
        assertThat(byState.content()).extracting(AdminStaffResponse::id).containsExactly(inFreshState.id());
    }

    @Test
    void staffListAndGet_includeResolvedCityAndStateNames() {
        City jaipur = createTestCity();
        AdminStaffResponse staff = createStaffAsSuperAdmin("City Display Staff " + unique(), List.of());
        staffAddressService.update(staff.id(),
                new UpdateAddressRequest("1 MG Road", null, jaipur.getStateId(), jaipur.getId(), "302001"),
                "127.0.0.1", "JUnit");

        AdminStaffResponse fetched = staffService.get(staff.id());
        assertThat(fetched.city()).isEqualTo(jaipur.getName());
        assertThat(fetched.state()).isNotNull();

        PageResponse<AdminStaffResponse> listed = staffService.list(
                new AdminStaffSearchCriteria(null, null, null, null, null, Set.of(jaipur.getId())),
                PageRequest.of(0, 50));
        assertThat(listed.content()).extracting(AdminStaffResponse::city).contains(jaipur.getName());
    }

    @Test
    void staffWithNoAddressSaved_cityAndStateAreNull() {
        AdminStaffResponse staff = createStaffAsSuperAdmin("No Address Display " + unique(), List.of());

        AdminStaffResponse fetched = staffService.get(staff.id());

        assertThat(fetched.city()).isNull();
        assertThat(fetched.state()).isNull();
    }

    // ---- role list: pagination, search, sort ------------------------------------------------------

    @Test
    void roleList_paginatesAndSearchesByPartialName() {
        String marker = "Rolesearch" + unique();
        AdminRoleResponse role = createRoleAsSuperAdmin(marker + " Role", "d", List.of());

        PageResponse<AdminRoleResponse> result = roleService.list(marker, null, PageRequest.of(0, 20));

        assertThat(result.content()).extracting(AdminRoleResponse::id).containsExactly(role.id());
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
    }

    @Test
    void roleList_filtersBySystemRoleFlag() {
        String marker = "SysRoleFilter" + unique();
        AdminRoleResponse role = createRoleAsSuperAdmin(marker + " Role", "d", List.of());

        PageResponse<AdminRoleResponse> nonSystemOnly = roleService.list(marker, false, PageRequest.of(0, 20));
        assertThat(nonSystemOnly.content()).extracting(AdminRoleResponse::id).containsExactly(role.id());

        PageResponse<AdminRoleResponse> systemOnly = roleService.list(marker, true, PageRequest.of(0, 20));
        assertThat(systemOnly.content()).isEmpty();
    }

    @Test
    void roleList_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> roleService.list(null, null, PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    private AuthTokens loginFully(AdminStaffResponse staff) {
        LoginResult login = authService.login(staff.email(), "BrandNew@12345", "127.0.0.1", "JUnit");
        String otp = extractOtp(emailSender.last().htmlBody());
        return authService.verifyOtp(login.challengeId(), otp, "127.0.0.1", "JUnit");
    }

    @TestConfiguration
    static class EmailTestConfig {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }
}
