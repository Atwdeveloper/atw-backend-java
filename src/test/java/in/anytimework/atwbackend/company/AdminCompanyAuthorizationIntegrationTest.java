package in.anytimework.atwbackend.company;

import in.anytimework.atwbackend.company.api.admin.AdminCompanyController;
import in.anytimework.atwbackend.company.api.admin.dto.BulkUpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyExportRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CreateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.domain.CompanyAddressType;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.identity.api.role.dto.AdminRoleResponse;
import in.anytimework.atwbackend.identity.api.role.dto.CreateAdminRoleRequest;
import in.anytimework.atwbackend.identity.api.staff.dto.AdminStaffResponse;
import in.anytimework.atwbackend.identity.api.staff.dto.CreateAdminStaffRequest;
import in.anytimework.atwbackend.identity.application.AdminRoleService;
import in.anytimework.atwbackend.identity.application.AdminStaffService;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves EVERY mutating (and view) {@code AdminCompanyController} endpoint genuinely rejects a
 * caller lacking the matching {@code company.*} permission code - this is the single most important
 * regression test for this module: the locked spec calls this out as the biggest bug in the legacy
 * reference app (almost no admin company action there checked a permission code at all), and
 * {@code AdminCompanyController} is the first controller in this codebase to get a dedicated test
 * proving its {@code @PreAuthorize} annotations actually do something.
 *
 * <p>Each denial is asserted by calling the real, Spring-proxied controller bean under a simulated
 * {@code SecurityContext} - mirrors {@code AdminStaffRoleIntegrationTest.actingAs}, the closest
 * established convention in this codebase for "run this as a specific admin actor", adapted to a
 * controller instead of a service since every {@code company.*} check lives on
 * {@code AdminCompanyController}, not {@code CompanyService} (see that class's own Javadoc on why).
 * {@code @PreAuthorize}'s method-security interceptor runs before the target method body, so every
 * denial case below never actually touches the database and needs no real fixture - arguments are
 * placeholders.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminCompanyAuthorizationIntegrationTest {

    @Autowired
    private AdminCompanyController controller;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private AdminRoleService roleService;
    @Autowired
    private AdminStaffService staffService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Copied from {@code AdminStaffRoleIntegrationTest.actingAs} - builds a token carrying the same
     * claims the real token-issuing path would, then runs it through the real
     * {@code JwtAuthenticationConverter} bean, so {@code PermissionChecker}/{@code @PreAuthorize}
     * behave identically to production rather than against a hand-rolled authorities list.
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

    /** A throwaway super-admin, purely so role/staff setup can run without tripping the privilege-escalation guard. */
    private UUID createSuperAdminActor() {
        AdminUser superAdmin = adminUsers.save(new AdminUser("authz-actor-" + unique(),
                "authz-actor-" + unique() + "@example.com", passwordEncoder.encode("Whatever@123"),
                "Setup Actor", null, true, false));
        return superAdmin.getId();
    }

    /** A staff admin holding exactly the given permission codes (no codes at all when called with none). */
    private UUID createStaffWithPermissions(String... codes) {
        UUID setupActor = createSuperAdminActor();
        AdminRoleResponse role = actingAs(setupActor, () -> roleService.create(
                new CreateAdminRoleRequest("Authz Test " + unique(), null, List.of(codes)), "127.0.0.1", "JUnit"));
        AdminStaffResponse staff = actingAs(setupActor, () -> staffService.create(
                new CreateAdminStaffRequest(null, "authz-staff-" + unique() + "@example.com", "Authz Actor", null,
                        List.of(role.id())), "127.0.0.1", "JUnit"));
        return staff.id();
    }

    private UUID aRealIndustryId() {
        UUID setupActor = createSuperAdminActor();
        IndustryResponse industry = actingAs(setupActor,
                () -> industryService.create(new CreateIndustryRequest("Authz Test Industry " + unique())));
        return industry.id();
    }

    // ---- every mutating endpoint: a caller with NO company.* permission is rejected -----------

    @Test
    void create_withoutCompanyCreatePermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        CreateCompanyRequest request = new CreateCompanyRequest("Nope Co " + unique(), UUID.randomUUID(), null, null,
                List.of(), "Owner", "owner-" + unique() + "@example.com", null);

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void update_withoutCompanyEditPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        UpdateCompanyRequest request = new UpdateCompanyRequest("Nope Co", UUID.randomUUID());

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.update(UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void updateStatus_withoutCompanyManageStatusPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        UpdateCompanyStatusRequest request = new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null);

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.updateStatus(UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void delete_withoutCompanyDeletePermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.delete(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void verifyGst_withoutCompanyEditPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.verifyGst(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void addAddress_withoutCompanyEditPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        CompanyAddressRequest request = new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true,
                "L1", null, null, null, null);

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.addAddress(UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void updateAddress_withoutCompanyEditPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        UpdateCompanyAddressRequest request = new UpdateCompanyAddressRequest(true, "L1", null, null, null, null);

        actingAs(noPerms, () -> assertThatThrownBy(
                () -> controller.updateAddress(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void deleteAddress_withoutCompanyEditPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(
                () -> controller.deleteAddress(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class));
    }

    // ---- the two read endpoints also require company.view - not left open to any admin --------

    @Test
    void list_withoutCompanyViewPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(
                () -> controller.list(null, null, null, null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void get_withoutCompanyViewPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.get(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void statusHistory_withoutCompanyViewPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(
                () -> controller.statusHistory(UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void listAddresses_withoutCompanyViewPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.listAddresses(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class));
    }

    // ---- sanity check the other direction too: holding the right permission is NOT also denied -

    @Test
    void create_withCompanyCreatePermission_isAllowed() {
        UUID withPerm = createStaffWithPermissions("company.create");
        CreateCompanyRequest request = new CreateCompanyRequest("Authz Co " + unique(), aRealIndustryId(), null, null,
                List.of(), "Owner " + unique(), "owner-" + unique() + "@example.com", null);

        CompanyResponse response = actingAs(withPerm, () -> controller.create(request).data());

        assertThat(response.status()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
    }

    @Test
    void list_withCompanyViewPermission_isAllowed() {
        UUID withPerm = createStaffWithPermissions("company.view");

        assertThatCode(() -> actingAs(withPerm,
                () -> controller.list(null, null, null, null, null, null, null, null, PageRequest.of(0, 20))))
                .doesNotThrowAnyException();
    }

    @Test
    void superAdmin_bypassesEveryCompanyPermissionCheck() {
        // The documented bypass (PermissionChecker's own Javadoc) - confirms it also covers the
        // company module specifically, not just the admin_staff/role endpoints already proven elsewhere.
        UUID superAdmin = createSuperAdminActor();

        assertThatCode(() -> actingAs(superAdmin,
                () -> controller.list(null, null, null, null, null, null, null, null, PageRequest.of(0, 20))))
                .doesNotThrowAnyException();
    }

    // ---- PART 2/3: the 3 new endpoints' own permission checks (manage_status/delete/export) ------

    @Test
    void bulkUpdateStatus_withoutCompanyManageStatusPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        BulkUpdateCompanyStatusRequest request = new BulkUpdateCompanyStatusRequest(Set.of(UUID.randomUUID()), CompanyStatus.ACTIVE, null);

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.bulkUpdateStatus(request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void bulkDelete_withoutCompanyDeletePermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        BulkIdsRequest request = new BulkIdsRequest(Set.of(UUID.randomUUID()));

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.bulkDelete(request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void export_withoutCompanyExportPermission_isRejected() {
        UUID noPerms = createStaffWithPermissions();
        CompanyExportRequest request = new CompanyExportRequest(null, null, null, null, null, null, null, null, null);

        actingAs(noPerms, () -> assertThatThrownBy(() -> controller.export(request))
                .isInstanceOf(AccessDeniedException.class));
    }

    @Test
    void bulkUpdateStatus_withCompanyManageStatusPermission_isAllowed() {
        UUID withPerm = createStaffWithPermissions("company.manage_status");
        BulkUpdateCompanyStatusRequest request = new BulkUpdateCompanyStatusRequest(Set.of(UUID.randomUUID()), CompanyStatus.ACTIVE, null);

        // Permission granted -> reaches the service (a "Not found" failure for the random id is the
        // guard working correctly, not an AccessDeniedException - proving the @PreAuthorize gate itself
        // let the call through).
        assertThatCode(() -> actingAs(withPerm, () -> controller.bulkUpdateStatus(request))).doesNotThrowAnyException();
    }

    @Test
    void export_withCompanyExportPermission_isAllowed() {
        UUID withPerm = createStaffWithPermissions("company.export");
        CompanyExportRequest request = new CompanyExportRequest(null, null, null, null, null, null, null, null, null);

        assertThatCode(() -> actingAs(withPerm, () -> controller.export(request))).doesNotThrowAnyException();
    }

    // ---- export: GST/PAN masking depends on company.view_sensitive, independently of company.export ----

    @Test
    void export_withoutViewSensitivePermission_masksGstAndPan_withItReturnsUnmasked() throws Exception {
        UUID setupActor = createSuperAdminActor();
        UUID industryId = aRealIndustryId();
        String gst = "27AAAPG" + unique().substring(0, 4) + "1ZV";
        String pan = "PAN" + unique().toUpperCase();
        CreateCompanyRequest createRequest = new CreateCompanyRequest("Masking Co " + unique(), industryId, gst, pan,
                List.of(), "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse created = actingAs(setupActor, () -> controller.create(createRequest).data());

        UUID exportOnly = createStaffWithPermissions("company.export");
        UUID exportAndViewSensitive = createStaffWithPermissions("company.export", "company.view_sensitive");
        CompanyExportRequest exportRequest = new CompanyExportRequest(Set.of(created.id()), null, null, null, null, null, null, null, null);

        byte[] maskedXlsx = actingAs(exportOnly, () -> controller.export(exportRequest).getBody());
        byte[] unmaskedXlsx = actingAs(exportAndViewSensitive, () -> controller.export(exportRequest).getBody());

        try (XSSFWorkbook maskedWorkbook = new XSSFWorkbook(new ByteArrayInputStream(maskedXlsx));
             XSSFWorkbook unmaskedWorkbook = new XSSFWorkbook(new ByteArrayInputStream(unmaskedXlsx))) {
            Row maskedRow = maskedWorkbook.getSheetAt(0).getRow(1);
            Row unmaskedRow = unmaskedWorkbook.getSheetAt(0).getRow(1);

            String maskedGst = maskedRow.getCell(4).getStringCellValue();
            String maskedPan = maskedRow.getCell(5).getStringCellValue();
            assertThat(maskedGst).isNotEqualTo(gst).endsWith(gst.substring(gst.length() - 4));
            assertThat(maskedGst).startsWith("*");
            assertThat(maskedPan).isNotEqualTo(pan).endsWith(pan.substring(pan.length() - 4));
            assertThat(maskedPan).startsWith("*");

            assertThat(unmaskedRow.getCell(4).getStringCellValue()).isEqualTo(gst);
            assertThat(unmaskedRow.getCell(5).getStringCellValue()).isEqualTo(pan);
        }
    }
}
