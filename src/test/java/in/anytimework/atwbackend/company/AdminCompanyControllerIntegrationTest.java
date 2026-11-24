package in.anytimework.atwbackend.company;

import in.anytimework.atwbackend.company.api.admin.AdminCompanyController;
import in.anytimework.atwbackend.company.api.admin.dto.BulkUpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyAddressResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyExportRequest;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyFilterOptionsResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanyStatusHistoryResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CompanySummaryResponse;
import in.anytimework.atwbackend.company.api.admin.dto.CreateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyAddressRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyRequest;
import in.anytimework.atwbackend.company.api.admin.dto.UpdateCompanyStatusRequest;
import in.anytimework.atwbackend.company.domain.CompanyAddressType;
import in.anytimework.atwbackend.company.domain.CompanyRefreshToken;
import in.anytimework.atwbackend.company.domain.CompanyRefreshTokenRepository;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.company.domain.CompanyUser;
import in.anytimework.atwbackend.company.domain.CompanyUserRepository;
import in.anytimework.atwbackend.company.domain.CompanyUserRole;
import in.anytimework.atwbackend.company.domain.CompanyUserStatus;
import in.anytimework.atwbackend.company.application.CompanyFilterOptionsService;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.lookup.CitySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.lookup.StateSummary;
import in.anytimework.atwbackend.masterdata.domain.City;
import in.anytimework.atwbackend.masterdata.domain.CityRepository;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for the admin-side company CRUD/lifecycle/address surface against the real
 * local Postgres - mirrors {@code DesignationAdminIntegrationTest}'s style, calling the real,
 * Spring-proxied {@code AdminCompanyController} bean (not raw HTTP) under a super-admin
 * {@code SecurityContext} set up once per test in {@code @BeforeEach} (the super-admin bypass is
 * proven separately, once, by {@code AdminCompanyAuthorizationIntegrationTest} - this class exists to
 * exercise the actual business behavior, not re-prove the permission gate on every call).
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminCompanyControllerIntegrationTest {

    @Autowired
    private AdminCompanyController controller;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired
    private CompanyUserRepository companyUsers;
    @Autowired
    private CompanyRefreshTokenRepository companyRefreshTokens;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private GeographyLookupService geography;
    @Autowired
    private CompanyFilterOptionsService filterOptionsService;
    @Autowired
    private StateRepository states;
    @Autowired
    private CityRepository cities;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record StateCity(UUID stateId, String stateName, UUID cityId, String cityName) {
    }

    /**
     * Scans the seeded masterdata for states that actually have at least one city on file
     * (V21__seed_city_data.sql), rather than assuming a fixed id - test data is seed data, not a
     * hardcoded fixture. Mirrors {@code WorkerBulkOperationsIntegrationTest}'s identical helper.
     */
    private List<StateCity> seededStateCitiesWithCities(int count) {
        List<StateCity> result = new ArrayList<>();
        for (var state : geography.listStates()) {
            var cities = geography.listCities(state.id());
            if (!cities.isEmpty()) {
                result.add(new StateCity(state.id(), state.name(), cities.get(0).id(), cities.get(0).name()));
                if (result.size() == count) {
                    return result;
                }
            }
        }
        throw new IllegalStateException("Need " + count + " seeded states with at least one city each for this test");
    }

    @BeforeEach
    void actAsSuperAdmin() {
        AdminUser superAdmin = adminUsers.save(new AdminUser("crud-actor-" + unique(),
                "crud-actor-" + unique() + "@example.com", passwordEncoder.encode("Whatever@123"),
                "CRUD Test Actor", null, true, false));
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("sub", superAdmin.getId().toString())
                .claim("super_admin", true)
                .claim("authorities", List.of("SUPER_ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        Authentication authentication = jwtAuthenticationConverter.convert(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UUID createIndustry() {
        return industryService.create(new CreateIndustryRequest("CRUD Test Industry " + unique())).id();
    }

    private CompanyResponse createCompany(String gst, String pan, List<CompanyAddressRequest> addresses) {
        CreateCompanyRequest request = new CreateCompanyRequest("CRUD Co " + unique(), createIndustry(), gst, pan,
                addresses, "Owner " + unique(), "owner-" + unique() + "@example.com", null);
        return controller.create(request).data();
    }

    private CompanyUser ownerOf(UUID companyId) {
        return companyUsers.findByCompanyId(companyId).stream()
                .filter(u -> u.getRole() == CompanyUserRole.OWNER)
                .findFirst().orElseThrow();
    }

    // ---- create / get -------------------------------------------------------------------------

    @Test
    void create_thenGet_roundTripsWithOwnerAndAddress() {
        CompanyAddressRequest address = new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true,
                "221B Civil Lines", null, null, null, "302001");
        String ownerEmail = "owner-" + unique() + "@example.com";
        CreateCompanyRequest request = new CreateCompanyRequest("Acme Builders " + unique(), createIndustry(), null,
                null, List.of(address), "Ravi Shah", ownerEmail, "9876543210");

        CompanyResponse created = controller.create(request).data();

        assertThat(created.status()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
        assertThat(created.companyCode()).startsWith("CMP");
        assertThat(created.gstVerifiedAt()).isNull();
        assertThat(created.owner()).isNotNull();
        assertThat(created.owner().email()).isEqualTo(ownerEmail);
        assertThat(created.addresses()).hasSize(1);
        assertThat(created.addresses().get(0).primary()).isTrue();
        assertThat(created.addresses().get(0).line1()).isEqualTo("221B Civil Lines");

        CompanyResponse fetched = controller.get(created.id()).data();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.owner().id()).isEqualTo(created.owner().id());
    }

    @Test
    void create_alsoProvisionsAnActiveOwnerAccount_withAnInviteTokenPersisted() {
        CompanyResponse created = createCompany(null, null, List.of());

        CompanyUser owner = ownerOf(created.id());
        assertThat(owner.getRole()).isEqualTo(CompanyUserRole.OWNER);
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.getPasswordHash()).isNotBlank();

        // Bypasses JPA to confirm the invite token row really was persisted - the raw token itself is
        // never exposed anywhere except the (asynchronously-sent) invite email, so this cannot go
        // through CompanyAuthTokenRepository.findByTokenHash without capturing that email.
        Integer tokenCount = jdbcTemplate.queryForObject(
                "select count(*) from company_auth_token where company_user_id = ? and type = 'INVITE'",
                Integer.class, owner.getId());
        assertThat(tokenCount).isEqualTo(1);
    }

    @Test
    void create_withDuplicateGstNumber_evenWithDifferentCaseAndWhitespace_isRejected() {
        // A fresh, run-unique base value (not a fixed literal) - this suite's test DB is a long-lived,
        // never-reset local Postgres instance (see worker-kyc-module-gotchas), so a hardcoded GSTIN
        // here would only pass on the very first run and then permanently fail on every later one.
        String gst = "GST" + unique().toUpperCase();
        createCompany(gst, null, List.of());

        assertThatThrownBy(() -> createCompany(" " + gst.toLowerCase() + " ", null, List.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withUnknownIndustry_isRejected() {
        CreateCompanyRequest request = new CreateCompanyRequest("Bad Industry Co " + unique(), UUID.randomUUID(),
                null, null, List.of(), "Owner", "owner-" + unique() + "@example.com", null);

        assertThatThrownBy(() -> controller.create(request)).isInstanceOf(ValidationException.class);
    }

    // ---- list: filters, search, sort -----------------------------------------------------------

    @Test
    void list_filtersByStatusAndIndustryAndSearch() {
        String marker = "ListFilter" + unique();
        UUID industryId = createIndustry();
        CreateCompanyRequest request = new CreateCompanyRequest(marker + " Co", industryId, null, null, List.of(),
                "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse created = controller.create(request).data();

        PageResponse<CompanySummaryResponse> bySearch = controller.list(null, null, marker, null, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(bySearch.content()).extracting(CompanySummaryResponse::id).containsExactly(created.id());

        PageResponse<CompanySummaryResponse> byStatus = controller
                .list(Set.of(CompanyStatus.PENDING_VERIFICATION), Set.of(industryId), marker, null, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(byStatus.content()).extracting(CompanySummaryResponse::id).containsExactly(created.id());

        PageResponse<CompanySummaryResponse> wrongStatus = controller
                .list(Set.of(CompanyStatus.ACTIVE), null, marker, null, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(wrongStatus.content()).isEmpty();
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null, null, PageRequest.of(0, 10, Sort.by("gstNumber"))))
                .isInstanceOf(ValidationException.class);
    }

    // ---- list: new PART 1 filters (gstVerified/createdFrom/createdTo/stateId/cityId) ------------

    @Test
    void list_filtersByGstVerified() {
        String marker = "GstVerifiedFilter" + unique();
        UUID industryId = createIndustry();
        CreateCompanyRequest unverifiedRequest = new CreateCompanyRequest(marker + " Unverified", industryId, null, null,
                List.of(), "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse unverified = controller.create(unverifiedRequest).data();
        CreateCompanyRequest verifiedRequest = new CreateCompanyRequest(marker + " Verified", industryId,
                "27AAAPG" + unique().substring(0, 4) + "1ZV", null, List.of(), "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse verified = controller.create(verifiedRequest).data();
        controller.verifyGst(verified.id());

        PageResponse<CompanySummaryResponse> verifiedOnly = controller
                .list(null, null, marker, true, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(verifiedOnly.content()).extracting(CompanySummaryResponse::id).containsExactly(verified.id());

        PageResponse<CompanySummaryResponse> unverifiedOnly = controller
                .list(null, null, marker, false, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(unverifiedOnly.content()).extracting(CompanySummaryResponse::id).containsExactly(unverified.id());

        PageResponse<CompanySummaryResponse> both = controller
                .list(null, null, marker, null, null, null, null, null, PageRequest.of(0, 20)).data();
        assertThat(both.content()).extracting(CompanySummaryResponse::id)
                .containsExactlyInAnyOrder(verified.id(), unverified.id());
    }

    @Test
    void list_filtersByCreatedFromAndCreatedTo() {
        String marker = "CreatedRangeFilter" + unique();
        CreateCompanyRequest request = new CreateCompanyRequest(marker + " Co", createIndustry(), null, null,
                List.of(), "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse created = controller.create(request).data();
        // CompanySpecifications.createdBetween deliberately interprets createdFrom/createdTo as whole
        // calendar days in UTC (see its own Javadoc, "same semantics as
        // WorkerSpecifications.registeredBetween") - a bare LocalDate.now() uses the JVM's default
        // zone instead, which disagrees with UTC for part of every day; pin "today" to UTC to match
        // what the filter under test actually compares against (hit for real once the wall-clock date
        // rolled over mid-session - see the identical fix in WorkerBulkOperationsIntegrationTest).
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        PageResponse<CompanySummaryResponse> withinRange = controller
                .list(null, null, marker, null, today, today, null, null, PageRequest.of(0, 20)).data();
        assertThat(withinRange.content()).extracting(CompanySummaryResponse::id).containsExactly(created.id());

        PageResponse<CompanySummaryResponse> beforeCreation = controller
                .list(null, null, marker, null, null, today.minusDays(1), null, null, PageRequest.of(0, 20)).data();
        assertThat(beforeCreation.content()).isEmpty();

        PageResponse<CompanySummaryResponse> afterCreation = controller
                .list(null, null, marker, null, today.plusDays(1), null, null, null, PageRequest.of(0, 20)).data();
        assertThat(afterCreation.content()).isEmpty();
    }

    // ---- filter-options: scoped values only ----------------------------------------------------

    /**
     * Proves actual scoping, not just "the endpoint returns something": a freshly created, never
     * referenced state/city/industry must NOT appear, while one actually assigned to a company must.
     * Unlike the state/city list() filter tests below (which can safely reuse a SEEDED state because
     * they're also scoped by a unique company-name search term), filter-options has no search
     * parameter at all - it scans every company unfiltered - so proving the negative case here
     * requires a genuinely fresh, never-before-used state/city (same technique and reasoning as
     * {@code AdminStaffRoleIntegrationTest.staffFilterOptions_onlyIncludesValuesActuallyReferencedByAStaffMember}).
     */
    @Test
    void companyFilterOptions_onlyIncludesValuesActuallyReferencedByACompany() {
        State referencedState = states.save(new State("FilterOptState " + unique(), unique().toUpperCase()));
        City referencedCity = cities.save(new City(referencedState.getId(), "FilterOptCity " + unique()));
        State unreferencedState = states.save(new State("UnusedFilterOptState " + unique(), unique().toUpperCase()));
        City unreferencedCity = cities.save(new City(unreferencedState.getId(), "UnusedFilterOptCity " + unique()));
        UUID referencedIndustryId = createIndustry();
        UUID unreferencedIndustryId = createIndustry();

        CompanyAddressRequest address = new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true,
                "1 Filter Street", null, referencedState.getId(), referencedCity.getId(), "302001");
        controller.create(new CreateCompanyRequest("FilterOpt Co " + unique(), referencedIndustryId, null, null,
                List.of(address), "Owner", "owner-" + unique() + "@example.com", null));

        CompanyFilterOptionsResponse options = filterOptionsService.get();

        assertThat(options.states()).extracting(StateSummary::id).contains(referencedState.getId());
        assertThat(options.states()).extracting(StateSummary::id).doesNotContain(unreferencedState.getId());
        assertThat(options.cities()).extracting(CitySummary::id).contains(referencedCity.getId());
        assertThat(options.cities()).extracting(CitySummary::id).doesNotContain(unreferencedCity.getId());
        assertThat(options.industries()).extracting(IndustrySummary::id).contains(referencedIndustryId);
        assertThat(options.industries()).extracting(IndustrySummary::id).doesNotContain(unreferencedIndustryId);
    }

    @Test
    void list_filtersByState_matchesACompanyWithAnyAddressReferencingIt() {
        String marker = "StateFilter" + unique();
        List<StateCity> stateCities = seededStateCitiesWithCities(2);
        StateCity stateA = stateCities.get(0);
        StateCity stateB = stateCities.get(1);

        // The company's NAME (not the address line) must carry the marker - search and stateId are
        // combined in the same list() call below so the marker scopes the result to this test's own
        // fresh rows, while stateId is the actual filter under test.
        CompanyResponse matches = controller.create(new CreateCompanyRequest(marker + " Match", createIndustry(),
                null, null, List.of(), "Owner", "owner-" + unique() + "@example.com", null)).data();
        controller.addAddress(matches.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, stateA.stateId(), null, null));
        CompanyResponse noMatch = controller.create(new CreateCompanyRequest(marker + " NoMatch", createIndustry(),
                null, null, List.of(), "Owner", "owner-" + unique() + "@example.com", null)).data();
        controller.addAddress(noMatch.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, stateB.stateId(), null, null));

        PageResponse<CompanySummaryResponse> result = controller
                .list(null, null, marker, null, null, null, Set.of(stateA.stateId()), null, PageRequest.of(0, 20)).data();

        assertThat(result.content()).extracting(CompanySummaryResponse::id).containsExactly(matches.id());
    }

    @Test
    void list_filtersByCity_matchesACompanyWithAnyAddressReferencingIt() {
        String marker = "CityFilter" + unique();
        List<StateCity> stateCities = seededStateCitiesWithCities(2);
        StateCity cityA = stateCities.get(0);
        StateCity cityB = stateCities.get(1);

        CompanyResponse matches = controller.create(new CreateCompanyRequest(marker + " Match", createIndustry(),
                null, null, List.of(), "Owner", "owner-" + unique() + "@example.com", null)).data();
        controller.addAddress(matches.id(),
                new CompanyAddressRequest(CompanyAddressType.BRANCH, true, "Office", null, cityA.stateId(), cityA.cityId(), null));
        CompanyResponse noMatch = controller.create(new CreateCompanyRequest(marker + " NoMatch", createIndustry(),
                null, null, List.of(), "Owner", "owner-" + unique() + "@example.com", null)).data();
        controller.addAddress(noMatch.id(),
                new CompanyAddressRequest(CompanyAddressType.BRANCH, true, "Office", null, cityB.stateId(), cityB.cityId(), null));

        PageResponse<CompanySummaryResponse> result = controller
                .list(null, null, marker, null, null, null, null, Set.of(cityA.cityId()), PageRequest.of(0, 20)).data();

        assertThat(result.content()).extracting(CompanySummaryResponse::id).containsExactly(matches.id());
    }

    // ---- bulk-status / bulk-delete: partial success through the real controller endpoints --------

    @Test
    void bulkUpdateStatus_partialFailure_reportsSuccessAndEachFailureSeparately() {
        CompanyResponse succeeds = createCompany(null, null, List.of());
        controller.updateStatus(succeeds.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null));
        CompanyResponse alreadyRejected = createCompany(null, null, List.of());
        controller.updateStatus(alreadyRejected.id(), new UpdateCompanyStatusRequest(CompanyStatus.REJECTED, "Incomplete"));
        UUID unknownId = UUID.randomUUID();

        BulkActionResult result = controller.bulkUpdateStatus(new BulkUpdateCompanyStatusRequest(
                Set.of(succeeds.id(), alreadyRejected.id(), unknownId), CompanyStatus.SUSPENDED, "Non-payment")).data();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(alreadyRejected.id(), unknownId);
        assertThat(result.failures()).filteredOn(f -> f.id().equals(unknownId))
                .extracting(BulkActionFailure::reason).containsExactly("Not found");

        // The one real success was actually applied, not merely counted.
        assertThat(controller.get(succeeds.id()).data().status()).isEqualTo(CompanyStatus.SUSPENDED);
    }

    @Test
    void bulkDelete_partialSuccess_activeCompanyBlocksOnlyItself() {
        CompanyResponse deletable = createCompany(null, null, List.of());
        controller.updateStatus(deletable.id(), new UpdateCompanyStatusRequest(CompanyStatus.REJECTED, "Incomplete"));
        CompanyResponse activeCompany = createCompany(null, null, List.of());
        controller.updateStatus(activeCompany.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null));
        UUID unknownId = UUID.randomUUID();

        BulkActionResult result = controller.bulkDelete(
                new BulkIdsRequest(Set.of(deletable.id(), activeCompany.id(), unknownId))).data();

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(activeCompany.id(), unknownId);

        assertThatThrownBy(() -> controller.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        // The failed id's own guard must not affect the other ids in the same batch.
        assertThat(controller.get(activeCompany.id()).data().status()).isEqualTo(CompanyStatus.ACTIVE);
    }

    // ---- export: real .xlsx content, parsed back with POI - including GST/PAN masking -------------

    @Test
    void export_byIds_producesXlsxWithExpectedColumnsAndMaskedGstPan() throws Exception {
        // pan_number has an active-row uniqueness constraint and this local test DB is never reset
        // between runs (see this file's own gotcha note on create_withDuplicateGstNumber...) - a fixed
        // PAN literal would only pass on the very first run, so this is run-unique like the GST value.
        String pan = "PAN" + unique().toUpperCase();
        CompanyResponse created = createCompany("27AAAPG" + unique().substring(0, 4) + "1ZV", pan, List.of());
        controller.verifyGst(created.id());

        byte[] xlsx = controller.export(new CompanyExportRequest(Set.of(created.id()), null, null, null, null, null, null, null, null)).getBody();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Company Code");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Name");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Industry");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Status");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("GST Number");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("PAN Number");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("GST Verified");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Created At");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo(created.companyCode());
            assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("PENDING_VERIFICATION");
            assertThat(dataRow.getCell(6).getStringCellValue()).isEqualTo("Yes");
            // This file always acts as a super-admin (@BeforeEach actAsSuperAdmin), which bypasses
            // PermissionChecker entirely (see its own Javadoc) - so GST/PAN export unmasked here. The
            // masked-vs-unmasked comparison itself (a real non-super-admin actor with and without
            // company.view_sensitive) is proven in AdminCompanyAuthorizationIntegrationTest, which
            // already has the actingAs()/createStaffWithPermissions() machinery for that.
            assertThat(dataRow.getCell(4).getStringCellValue()).endsWith("1ZV");
            assertThat(dataRow.getCell(5).getStringCellValue()).isEqualTo(pan);
        }
    }

    @Test
    void export_withNoIds_fallsBackToFilterCriteria() throws Exception {
        String marker = "ExportByFilter" + unique();
        CreateCompanyRequest request = new CreateCompanyRequest(marker + " Co", createIndustry(), null, null,
                List.of(), "Owner", "owner-" + unique() + "@example.com", null);
        CompanyResponse created = controller.create(request).data();

        byte[] xlsx = controller.export(new CompanyExportRequest(null, null, null, marker, null, null, null, null, null)).getBody();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1); // header + exactly this one matching row
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(created.companyCode());
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEmpty(); // no GST on file -> blank cell
        }
    }

    // ---- update profile -------------------------------------------------------------------------

    @Test
    void update_replacesNameAndIndustry_butNeverGstPanOrStatus() {
        // No GST/PAN needed here (the test proves update() never touches them, not what a verified
        // value looks like) - a fixed literal would only pass on the very first run against this
        // suite's long-lived, never-reset local atw_test database (see worker-kyc-module-gotchas).
        CompanyResponse created = createCompany(null, null, List.of());
        UUID newIndustry = createIndustry();

        CompanyResponse updated = controller.update(created.id(), new UpdateCompanyRequest("Renamed Co " + unique(), newIndustry)).data();

        assertThat(updated.name()).startsWith("Renamed Co");
        assertThat(updated.industryId()).isEqualTo(newIndustry);
        assertThat(updated.status()).isEqualTo(CompanyStatus.PENDING_VERIFICATION);
        assertThat(updated.gstVerifiedAt()).isNull();
    }

    // ---- status lifecycle + the user-disable/reactivate/revoke cascade --------------------------

    @Test
    void updateStatus_fullLifecycle_cascadesDisableOnSuspendAndReactivateOnResume() {
        CompanyResponse created = createCompany(null, null, List.of());
        CompanyUser owner = ownerOf(created.id());
        CompanyRefreshToken token = companyRefreshTokens.save(new CompanyRefreshToken(owner.getId(),
                UUID.randomUUID(), "hash-" + unique(), Instant.now().plusSeconds(3600), "127.0.0.1", "JUnit"));

        controller.updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null));
        CompanyResponse suspended = controller
                .updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.SUSPENDED, "Non-payment")).data();
        assertThat(suspended.status()).isEqualTo(CompanyStatus.SUSPENDED);

        assertThat(companyUsers.findByCompanyId(created.id()).get(0).getStatus()).isEqualTo(CompanyUserStatus.DISABLED);
        CompanyRefreshToken revoked = companyRefreshTokens.findByTokenHash(token.getTokenHash()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();

        CompanyResponse resumed = controller
                .updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null)).data();
        assertThat(resumed.status()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(companyUsers.findByCompanyId(created.id()).get(0).getStatus()).isEqualTo(CompanyUserStatus.ACTIVE);
    }

    @Test
    void updateStatus_rejectedToActiveDirectly_isRejected() {
        CompanyResponse created = createCompany(null, null, List.of());
        controller.updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.REJECTED, "Incomplete"));

        assertThatThrownBy(() -> controller.updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void statusHistory_recordsEveryTransitionNewestFirst() {
        CompanyResponse created = createCompany(null, null, List.of());
        controller.updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.ACTIVE, null));
        controller.updateStatus(created.id(), new UpdateCompanyStatusRequest(CompanyStatus.SUSPENDED, "Non-payment"));

        PageResponse<CompanyStatusHistoryResponse> history = controller.statusHistory(created.id(), PageRequest.of(0, 10)).data();

        assertThat(history.content()).extracting(CompanyStatusHistoryResponse::toStatus)
                .containsExactly(CompanyStatus.SUSPENDED, CompanyStatus.ACTIVE, CompanyStatus.PENDING_VERIFICATION);
    }

    // ---- delete: soft delete + the same disable cascade -----------------------------------------

    @Test
    void delete_softDeletes_disappearsFromGet_andDisablesTheOwner() {
        CompanyResponse created = createCompany(null, null, List.of());

        controller.delete(created.id());

        assertThatThrownBy(() -> controller.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(companyUsers.findByCompanyId(created.id()).get(0).getStatus()).isEqualTo(CompanyUserStatus.DISABLED);
    }

    // ---- GST verification (stub adapter, the test/local default) --------------------------------

    @Test
    void verifyGst_withStubAdapter_setsVerifiedAtAndRef() {
        CompanyResponse created = createCompany("27AAAPG" + unique().substring(0, 4) + "1ZV", null, List.of());

        CompanyResponse verified = controller.verifyGst(created.id()).data();

        assertThat(verified.gstVerifiedAt()).isNotNull();
        assertThat(verified.gstVerificationRef()).isEqualTo("STUB-GST-VERIFICATION-REF");
    }

    @Test
    void verifyGst_withNoGstNumberOnFile_isRejected() {
        CompanyResponse created = createCompany(null, null, List.of());

        assertThatThrownBy(() -> controller.verifyGst(created.id())).isInstanceOf(ValidationException.class);
    }

    // ---- address sub-resource: add/update/delete, primary swap, and the IDOR guard --------------

    @Test
    void addresses_addSecondPrimary_demotesTheFirst() {
        CompanyResponse created = createCompany(null, null, List.of());

        CompanyAddressResponse first = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, null, null, null)).data();
        CompanyAddressResponse second = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.BRANCH, true, "Branch", null, null, null, null)).data();

        List<CompanyAddressResponse> all = controller.listAddresses(created.id()).data();
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(CompanyAddressResponse::primary).extracting(CompanyAddressResponse::id)
                .containsExactly(second.id());
        assertThat(first.primary()).isTrue(); // the response returned at add-time, before being demoted by the second call
    }

    @Test
    void addresses_update_canReclaimPrimary_andDelete() {
        CompanyResponse created = createCompany(null, null, List.of());
        CompanyAddressResponse first = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, null, null, null)).data();
        controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.BRANCH, true, "Branch", null, null, null, null));

        CompanyAddressResponse updated = controller.updateAddress(created.id(), first.id(),
                new UpdateCompanyAddressRequest(true, "Office Renamed", null, null, null, null)).data();
        assertThat(updated.primary()).isTrue();
        assertThat(updated.line1()).isEqualTo("Office Renamed");

        controller.deleteAddress(created.id(), first.id());
        List<CompanyAddressResponse> remaining = controller.listAddresses(created.id()).data();
        assertThat(remaining).extracting(CompanyAddressResponse::id).doesNotContain(first.id());
    }

    @Test
    void addresses_delete_onlyRemainingAddress_isBlocked() {
        CompanyResponse created = createCompany(null, null, List.of());
        CompanyAddressResponse only = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, null, null, null)).data();

        assertThatThrownBy(() -> controller.deleteAddress(created.id(), only.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one address");

        List<CompanyAddressResponse> remaining = controller.listAddresses(created.id()).data();
        assertThat(remaining).extracting(CompanyAddressResponse::id).containsExactly(only.id());
    }

    @Test
    void addresses_delete_primaryAddress_softDeletesRow_andPromotesSurvivorToPrimary() {
        // Real-DB regression for the V38 migration: redefining uq_company_address_primary to exclude
        // deleted_at IS NOT NULL rows is what makes the promote-on-delete below possible at all - with
        // the old (pre-V38) index, writing is_primary=true on the survivor while the soft-deleted row
        // still physically holds is_primary=true would throw a unique-constraint violation.
        CompanyResponse created = createCompany(null, null, List.of());
        CompanyAddressResponse first = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "Office", null, null, null, null)).data();
        CompanyAddressResponse second = controller.addAddress(created.id(),
                new CompanyAddressRequest(CompanyAddressType.BRANCH, false, "Branch", null, null, null, null)).data();

        controller.deleteAddress(created.id(), first.id());

        // The soft-deleted row still physically exists (never a real SQL DELETE) - confirmed by
        // bypassing the entity's own @SQLRestriction via raw SQL, same technique as this file's
        // invite-token check above.
        Boolean stillPresentButDeleted = jdbcTemplate.queryForObject(
                "select deleted_at is not null from company_address where id = ?", Boolean.class, first.id());
        assertThat(stillPresentButDeleted).isTrue();

        List<CompanyAddressResponse> remaining = controller.listAddresses(created.id()).data();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).id()).isEqualTo(second.id());
        assertThat(remaining.get(0).primary()).isTrue();
    }

    @Test
    void addresses_cannotBeReachedThroughAnotherCompanysId() {
        CompanyResponse companyA = createCompany(null, null, List.of());
        CompanyAddressResponse addressOfA = controller.addAddress(companyA.id(),
                new CompanyAddressRequest(CompanyAddressType.REGISTERED_OFFICE, true, "A Office", null, null, null, null)).data();
        CompanyResponse companyB = createCompany(null, null, List.of());

        assertThatThrownBy(() -> controller.updateAddress(companyB.id(), addressOfA.id(),
                new UpdateCompanyAddressRequest(false, "Hijacked", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.deleteAddress(companyB.id(), addressOfA.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
