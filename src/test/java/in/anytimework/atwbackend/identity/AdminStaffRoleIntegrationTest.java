package in.anytimework.atwbackend.identity;

import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleDto;
import in.anytimework.atwbackend.identity.api.role.dto.UpdateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserBankDetailDto;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserKycDto;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminUserProfileDto;
import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminRoleRef;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffDto;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateBankDetailRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateKycRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateProfileRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.UpdateAdminStaffRequest;
import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.AdminRoleService;
import in.anytimework.atwbackend.identity.application.AdminStaffBankDetailService;
import in.anytimework.atwbackend.identity.application.AdminStaffKycService;
import in.anytimework.atwbackend.identity.application.AdminStaffProfileService;
import in.anytimework.atwbackend.identity.application.AdminStaffService;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminStatus;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.Gender;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.notification.CapturingEmailSender;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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

    private AdminStaffDto createStaffAsSuperAdmin(String fullName, List<UUID> roleIds) {
        UUID setupActor = createSuperAdminActor();
        return actingAs(setupActor, () -> staffService.create(
                new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com", fullName, null, roleIds),
                "127.0.0.1", "JUnit"));
    }

    /** Role creation with a non-empty permissionCodes list also trips PermissionEscalationGuard without an actor. */
    private AdminRoleDto createRoleAsSuperAdmin(String name, String description, List<String> permissionCodes) {
        UUID setupActor = createSuperAdminActor();
        return actingAs(setupActor, () -> roleService.create(
                new CreateAdminRoleRequest(name, description, permissionCodes), "127.0.0.1", "JUnit"));
    }

    @Test
    void fullLifecycle_createRoleThenStaff_acceptInvite_loginResolvesAssignedPermissions() {
        AdminRoleDto role = createRoleAsSuperAdmin("QA Tester " + unique(), "d",
                List.of("dashboard.view", "workers.view"));

        AdminStaffDto staff = createStaffAsSuperAdmin("New Staff", List.of(role.id()));
        assertThat(staff.status()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(staff.roles().stream().map(AdminRoleRef::name).toList()).containsExactly(role.name());

        String inviteToken = extractToken(emailSender.last().htmlBody());
        authService.acceptInvite(inviteToken, "BrandNew@12345", "127.0.0.1", "JUnit");

        LoginResult login = authService.login(staff.email(), "BrandNew@12345", "127.0.0.1", "JUnit");
        assertThat(login.otpRequired()).isTrue();
        String otp = extractOtp(emailSender.last().htmlBody());
        AuthTokens tokens = authService.verifyOtp(login.challengeId(), otp, "127.0.0.1", "JUnit");

        assertThat(tokens.admin().authorities()).containsExactlyInAnyOrder("dashboard.view", "workers.view");
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
        AdminRoleDto role = roleService.create(new CreateAdminRoleRequest("Assigned Role " + unique(), null, List.of()),
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
        AdminStaffDto staff = staffService.create(new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com",
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
        AdminStaffDto staff = staffService.create(new CreateAdminStaffRequest(null, "staff-" + unique() + "@example.com",
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
        AdminRoleDto limitedRole = createRoleAsSuperAdmin("Limited " + unique(), null, List.of("dashboard.view"));
        AdminRoleDto powerfulRole = createRoleAsSuperAdmin("Powerful " + unique(), null,
                List.of("dashboard.view", "admin_staff.delete"));
        AdminStaffDto limitedActor = createStaffAsSuperAdmin("Limited Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> staffService.create(new CreateAdminStaffRequest(null,
                "victim-" + unique() + "@example.com", "Victim", null, List.of(powerfulRole.id())),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void staffWithLimitedPermissions_cannotGrantAMorePrivilegedRoleOnUpdate() {
        AdminRoleDto limitedRole = createRoleAsSuperAdmin("Limited " + unique(), null, List.of("dashboard.view"));
        AdminRoleDto powerfulRole = createRoleAsSuperAdmin("Powerful " + unique(), null,
                List.of("dashboard.view", "admin_staff.delete"));
        AdminStaffDto limitedActor = createStaffAsSuperAdmin("Limited Actor", List.of(limitedRole.id()));
        AdminStaffDto victim = createStaffAsSuperAdmin("Victim", List.of());

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> staffService.update(victim.id(),
                new UpdateAdminStaffRequest(null, "Victim", null, List.of(powerfulRole.id())), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void staffProfile_updateThenGet_roundTrips() {
        AdminStaffDto staff = createStaffAsSuperAdmin("Profile Target", List.of());

        AdminUserProfileDto updated = staffProfileService.update(staff.id(),
                new UpdateProfileRequest(LocalDate.of(1995, 6, 15), Gender.FEMALE, "221B Baker Street", "560001"),
                "127.0.0.1", "JUnit");
        assertThat(updated.dateOfBirth()).isEqualTo(LocalDate.of(1995, 6, 15));

        AdminUserProfileDto fetched = staffProfileService.get(staff.id());
        assertThat(fetched.gender()).isEqualTo(Gender.FEMALE);
        assertThat(fetched.addressLine()).isEqualTo("221B Baker Street");
        assertThat(fetched.pincode()).isEqualTo("560001");
    }

    @Test
    void staffKyc_isEncryptedAtRest_andMaskedWithoutViewKycPermission() {
        AdminStaffDto target = createStaffAsSuperAdmin("KYC Target", List.of());
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

        AdminRoleDto viewOnly = createRoleAsSuperAdmin("View Only " + unique(), null, List.of("admin_staff.view"));
        AdminRoleDto viewKyc = createRoleAsSuperAdmin("View KYC " + unique(), null,
                List.of("admin_staff.view", "admin_staff.view_kyc"));
        AdminStaffDto viewOnlyActor = createStaffAsSuperAdmin("View Only Actor", List.of(viewOnly.id()));
        AdminStaffDto viewKycActor = createStaffAsSuperAdmin("View KYC Actor", List.of(viewKyc.id()));

        AdminUserKycDto masked = actingAs(viewOnlyActor.id(), () -> staffKycService.get(target.id()));
        assertThat(masked.aadhaarNumber()).isNotEqualTo("123456789012").endsWith("9012");
        assertThat(masked.panNumber()).isNotEqualTo("ABCDE1234F").endsWith("234F");

        AdminUserKycDto unmasked = actingAs(viewKycActor.id(), () -> staffKycService.get(target.id()));
        assertThat(unmasked.aadhaarNumber()).isEqualTo("123456789012");
        assertThat(unmasked.panNumber()).isEqualTo("ABCDE1234F");
    }

    @Test
    void staffBankDetail_accountNumberIsEncryptedAndMaskedWithoutViewBankDetailsPermission() {
        AdminStaffDto target = createStaffAsSuperAdmin("Bank Target", List.of());
        UUID setupActor = createSuperAdminActor();
        actingAs(setupActor, () -> staffBankDetailService.update(target.id(),
                new UpdateBankDetailRequest("Target Holder", "Test Bank", "000111222333", "TEST0001234", "MG Road"),
                "127.0.0.1", "JUnit"));

        String rawStored = jdbcTemplate.queryForObject(
                "select account_number from admin_user_bank_detail where admin_user_id = ?", String.class, target.id());
        assertThat(rawStored).isNotEqualTo("000111222333").doesNotContain("000111222333");

        AdminRoleDto viewOnly = createRoleAsSuperAdmin("View Only Bank " + unique(), null, List.of("admin_staff.view"));
        AdminStaffDto viewOnlyActor = createStaffAsSuperAdmin("View Only Bank Actor", List.of(viewOnly.id()));

        AdminUserBankDetailDto masked = actingAs(viewOnlyActor.id(), () -> staffBankDetailService.get(target.id()));
        assertThat(masked.accountNumber()).isNotEqualTo("000111222333").endsWith("2333");
        // Non-sensitive fields are never masked.
        assertThat(masked.bankName()).isEqualTo("Test Bank");
        assertThat(masked.ifscCode()).isEqualTo("TEST0001234");
    }

    @Test
    void updateProfile_onSuperAdminTarget_isRejected() {
        UUID superAdminId = createSuperAdminActor();

        assertThatThrownBy(() -> staffProfileService.update(superAdminId,
                new UpdateProfileRequest(null, null, "Somewhere", null), "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void roleCreate_cannotGrantAPermissionBeyondTheActorsOwn() {
        AdminRoleDto limitedRole = createRoleAsSuperAdmin("Limited RC " + unique(), null, List.of("dashboard.view"));
        AdminStaffDto limitedActor = createStaffAsSuperAdmin("Limited RC Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> roleService.create(
                new CreateAdminRoleRequest("Sneaky " + unique(), null, List.of("dashboard.view", "admin_staff.delete")),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void roleUpdate_cannotGrantAPermissionBeyondTheActorsOwn() {
        // The attack this guards against: editing an ALREADY-ASSIGNED role's permissions after the
        // fact, rather than assigning a new, more-powerful role - AdminStaffService's own guard only
        // ever sees an assignment event, not a later mutation of a role someone already holds.
        AdminRoleDto limitedRole = createRoleAsSuperAdmin("Limited RU " + unique(), null, List.of("dashboard.view"));
        AdminRoleDto targetRole = createRoleAsSuperAdmin("Target RU " + unique(), null, List.of("dashboard.view"));
        AdminStaffDto limitedActor = createStaffAsSuperAdmin("Limited RU Actor", List.of(limitedRole.id()));

        actingAs(limitedActor.id(), () -> assertThatThrownBy(() -> roleService.update(targetRole.id(),
                new UpdateAdminRoleRequest("Target RU", null, List.of("dashboard.view", "admin_staff.delete")),
                "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class));
    }

    @Test
    void changingStaffRoles_forcesReLogin_butAPlainProfileEditDoesNot() {
        AdminRoleDto roleA = roleService.create(new CreateAdminRoleRequest("Role A " + unique(), null, List.of()),
                "127.0.0.1", "JUnit");
        AdminRoleDto roleB = roleService.create(new CreateAdminRoleRequest("Role B " + unique(), null, List.of()),
                "127.0.0.1", "JUnit");
        AdminStaffDto staff = createStaffAsSuperAdmin("Role Change Target", List.of(roleA.id()));
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
        AdminStaffDto first = staffService.create(new CreateAdminStaffRequest("", "staff-" + unique() + "@example.com",
                "Empty Username First", null, List.of()), "127.0.0.1", "JUnit");
        AdminStaffDto second = staffService.create(new CreateAdminStaffRequest("", "staff-" + unique() + "@example.com",
                "Empty Username Second", null, List.of()), "127.0.0.1", "JUnit");

        assertThat(adminUsers.findById(first.id()).orElseThrow().getUsername()).isNull();
        assertThat(adminUsers.findById(second.id()).orElseThrow().getUsername()).isNull();
    }

    private AuthTokens loginFully(AdminStaffDto staff) {
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
