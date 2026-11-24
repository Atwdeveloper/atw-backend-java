package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.faceauth.domain.WorkerFaceEnrollmentRepository;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillResponse;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillService;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerAddressRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerFilterOptionsResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.application.WorkerAddressService;
import in.anytimework.atwbackend.worker.application.WorkerFilterOptionsService;
import in.anytimework.atwbackend.worker.application.WorkerSearchCriteria;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.AddressType;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatusHistoryRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the listing-API enhancement added on top of {@link WorkerService}'s original CRUD/status
 * surface (see {@link WorkerServiceIntegrationTest} for that original coverage, left untouched):
 * multiselect list filters, {@code bulkUpdateStatus}, {@code exportToExcel}, and single/bulk
 * {@code delete} - a soft delete (sets {@code deleted_at}, guarded by the worker being non-ACTIVE)
 * that preserves every satellite row and frees the worker's mobile number for reuse; see
 * {@code Worker}'s class-level Javadoc and {@code WorkerService.delete()}'s Javadoc for the full
 * mechanism. Kept in its own file rather than growing
 * {@link WorkerServiceIntegrationTest} further, matching this codebase's existing split between that
 * file and {@link WorkerSatelliteServicesIntegrationTest}/{@link WorkerKycVerificationFlowIntegrationTest}.
 *
 * <p>Every assertion below either filters by a freshly-generated, globally-unique id (designation/
 * mobile number) or scopes its {@code Set.of(...)} to ids it just created - never an
 * unfiltered {@code list()}/count - because the local test Postgres accumulates rows across every
 * test run in this session.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class WorkerBulkOperationsIntegrationTest {

    @Autowired
    private WorkerService workerService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private WorkerStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private FaceAuthService faceAuthService;
    @Autowired
    private WorkerFaceEnrollmentRepository faceEnrollmentRepository;
    @Autowired
    private WorkerAddressService addressService;
    @Autowired
    private WorkerFilterOptionsService filterOptionsService;
    @Autowired
    private GeographyLookupService geography;

    private static final byte[] FAKE_JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5
    };

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private DesignationResponse createDesignation(String label) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest(label + " Industry " + unique()));
        return designationService.create(new CreateDesignationRequest(label + " " + unique(), Set.of(industry.id()), industry.id(), null));
    }

    private WorkerResponse createWorker(UUID designationId) {
        return workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE, null,
                uniqueMobile(), null, null, null, Set.of(designationId), designationId, Set.of()));
    }

    private record StateCity(UUID stateId, String stateName, UUID cityId, String cityName) {
    }

    /** Scans the seeded masterdata for states that actually have at least one city on file (V21__seed_city_data.sql), rather than assuming a fixed id - test data is seed data, not a hardcoded fixture. */
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

    private static WorkerSearchCriteria emptyCriteria() {
        return new WorkerSearchCriteria(null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Reads a .xlsx export's cells back into a list of rows (row 0 is the header), each cell a
     * {@link String} or {@code null} for a blank cell - the same shape {@code ExcelWriter.write}
     * takes rows in, read back out.
     */
    private static List<List<String>> readExcelRows(byte[] xlsxBytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null || cell.getCellType() == CellType.BLANK ? null : cell.getStringCellValue());
                }
                rows.add(cells);
            }
            return rows;
        }
    }

    // ---- Multiselect filters ------------------------------------------------------------------

    @Test
    void list_filtersByMultipleDesignationIds_matchesWorkersUnderEither() {
        DesignationResponse designationA = createDesignation("MultiDesigA");
        DesignationResponse designationB = createDesignation("MultiDesigB");
        DesignationResponse designationC = createDesignation("MultiDesigC");

        WorkerResponse workerA = createWorker(designationA.id());
        WorkerResponse workerB = createWorker(designationB.id());
        WorkerResponse workerC = createWorker(designationC.id());

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designationA.id(), designationB.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id)
                .containsExactlyInAnyOrder(workerA.id(), workerB.id());
        assertThat(result.content()).extracting(WorkerSummaryResponse::id).doesNotContain(workerC.id());
    }

    @Test
    void list_filtersByMultipleStatuses_matchesWorkersInEither() {
        DesignationResponse designation = createDesignation("MultiStatus");

        WorkerResponse registeredWorker = createWorker(designation.id());
        WorkerResponse inactiveWorker = createWorker(designation.id());
        workerService.updateStatus(inactiveWorker.id(), WorkerStatus.INACTIVE, "Testing multiselect status filter");

        // Scoped to this test's own fresh designation id so accumulated data from other tests/runs
        // can never leak into the result set (gotcha: never trust an otherwise-unfiltered list()).
        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.REGISTERED, WorkerStatus.INACTIVE), null,
                        Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id)
                .containsExactlyInAnyOrder(registeredWorker.id(), inactiveWorker.id());
    }

    // ---- state/city (address) filters - match EITHER address type, see WorkerSearchCriteria -------

    @Test
    void list_filtersByState_matchesWorkerViaEitherAddressType() {
        DesignationResponse designation = createDesignation("StateFilter");
        List<StateCity> stateCities = seededStateCitiesWithCities(2);
        StateCity stateA = stateCities.get(0);
        StateCity stateB = stateCities.get(1);

        WorkerResponse currentMatches = createWorker(designation.id());
        addressService.update(currentMatches.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 Current St", null, stateA.stateId(), null, "100001"));

        WorkerResponse permanentMatches = createWorker(designation.id());
        addressService.update(permanentMatches.id(), AddressType.PERMANENT,
                new UpdateWorkerAddressRequest("1 Permanent St", null, stateA.stateId(), null, "100002"));

        WorkerResponse noMatch = createWorker(designation.id());
        addressService.update(noMatch.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 Other St", null, stateB.stateId(), null, "100003"));

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null,
                        Set.of(stateA.stateId()), null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id)
                .containsExactlyInAnyOrder(currentMatches.id(), permanentMatches.id());
        assertThat(result.content()).extracting(WorkerSummaryResponse::id).doesNotContain(noMatch.id());
    }

    @Test
    void list_filtersByCity_matchesWorkerViaEitherAddressType() {
        DesignationResponse designation = createDesignation("CityFilter");
        List<StateCity> stateCities = seededStateCitiesWithCities(2);
        StateCity cityA = stateCities.get(0);
        StateCity cityB = stateCities.get(1);

        WorkerResponse matches = createWorker(designation.id());
        addressService.update(matches.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 Match St", null, cityA.stateId(), cityA.cityId(), "100001"));

        WorkerResponse noMatch = createWorker(designation.id());
        addressService.update(noMatch.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 NoMatch St", null, cityB.stateId(), cityB.cityId(), "100002"));

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null,
                        null, Set.of(cityA.cityId()), null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id).containsExactly(matches.id());
    }

    // ---- city/state names in the listing/export (display only - see WorkerSummaryResponse) --------

    @Test
    void list_includesCurrentAndPermanentCityStateNames() {
        DesignationResponse designation = createDesignation("AddressInList");
        StateCity stateCity = seededStateCitiesWithCities(1).get(0);
        WorkerResponse worker = createWorker(designation.id());
        addressService.update(worker.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 Current St", null, stateCity.stateId(), stateCity.cityId(), "100001"));

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));

        WorkerSummaryResponse row = result.content().stream().filter(w -> w.id().equals(worker.id())).findFirst().orElseThrow();
        assertThat(row.currentCity()).isEqualTo(stateCity.cityName());
        assertThat(row.currentState()).isEqualTo(stateCity.stateName());
        assertThat(row.permanentCity()).isNull();
        assertThat(row.permanentState()).isNull();
    }

    @Test
    void exportToExcel_includesCityAndStateColumns() throws IOException {
        DesignationResponse designation = createDesignation("AddressInExcel");
        StateCity stateCity = seededStateCitiesWithCities(1).get(0);
        WorkerResponse worker = createWorker(designation.id());
        addressService.update(worker.id(), AddressType.PERMANENT,
                new UpdateWorkerAddressRequest("1 Permanent St", null, stateCity.stateId(), stateCity.cityId(), "100001"));

        byte[] xlsxBytes = workerService.exportToExcel(Set.of(worker.id()), emptyCriteria());
        List<List<String>> rows = readExcelRows(xlsxBytes);

        assertThat(rows.get(0)).containsExactly("Worker Code", "First Name", "Last Name", "Mobile Number", "Status",
                "KYC Status", "Designation", "Current City", "Current State", "Permanent City",
                "Permanent State", "Face Enrolled");
        assertThat(rows).anyMatch(row -> worker.workerCode().equals(row.get(0))
                && stateCity.cityName().equals(row.get(9)) && stateCity.stateName().equals(row.get(10)));
    }

    // ---- search ---------------------------------------------------------------------------------

    @Test
    void list_searchMatchesPersonalEmail() {
        DesignationResponse designation = createDesignation("SearchEmail");
        String email = "search" + unique() + "@example.com";
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE,
                null, uniqueMobile(), email, null, null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, email, null, null, null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id).containsExactly(worker.id());
    }

    // ---- registeredFrom/registeredTo (there is no separate "registration date" column - this
    // filters on createdAt, see WorkerSearchCriteria's Javadoc) -------------------------------------

    @Test
    void list_filtersByRegisteredDateRange_matchesTodayExcludesOutsideRange() {
        DesignationResponse designation = createDesignation("RegisteredToday");
        WorkerResponse worker = createWorker(designation.id());
        // WorkerSpecifications.registeredBetween deliberately interprets registeredFrom/registeredTo
        // as whole calendar days in UTC (see its own Javadoc) - a fixed, explicit zone, independent of
        // both the app's injected (Asia/Kolkata) business Clock and whatever zone the JVM happens to
        // default to. A bare LocalDate.now() here uses the JVM's default zone instead, which silently
        // disagrees with UTC for part of every day (and happened to fail this exact assertion once the
        // real calendar date rolled over mid-session) - pin "today" to UTC to match what the filter
        // under test actually compares against.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        PageResponse<WorkerSummaryResponse> withinRange = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null,
                        today, today, null, null, null),
                PageRequest.of(0, 50));
        assertThat(withinRange.content()).extracting(WorkerSummaryResponse::id).contains(worker.id());

        PageResponse<WorkerSummaryResponse> beforeRegistration = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null,
                        null, today.minusDays(1), null, null, null),
                PageRequest.of(0, 50));
        assertThat(beforeRegistration.content()).extracting(WorkerSummaryResponse::id).doesNotContain(worker.id());

        PageResponse<WorkerSummaryResponse> afterRegistration = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null,
                        today.plusDays(1), null, null, null, null),
                PageRequest.of(0, 50));
        assertThat(afterRegistration.content()).extracting(WorkerSummaryResponse::id).doesNotContain(worker.id());
    }

    // ---- sorting --------------------------------------------------------------------------------

    @Test
    void list_sortsByLastNameAscendingOrDescending() {
        DesignationResponse designation = createDesignation("SortWorker");
        String prefix = "SortW" + unique();
        WorkerResponse beta = workerService.create(new CreateWorkerRequest("Test", null, prefix + " Beta", null, Gender.MALE,
                null, uniqueMobile(), null, null, null, Set.of(designation.id()), designation.id(), Set.of()));
        WorkerResponse alpha = workerService.create(new CreateWorkerRequest("Test", null, prefix + " Alpha", null, Gender.MALE,
                null, uniqueMobile(), null, null, null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> ascending = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 100, Sort.by("lastName").ascending()));
        assertThat(ascending.content()).extracting(WorkerSummaryResponse::id).containsExactly(alpha.id(), beta.id());

        PageResponse<WorkerSummaryResponse> descending = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 100, Sort.by("lastName").descending()));
        assertThat(descending.content()).extracting(WorkerSummaryResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> workerService.list(emptyCriteria(), PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    // ---- bulkUpdateStatus -----------------------------------------------------------------------

    @Test
    void bulkUpdateStatus_partialFailure_reportsSuccessAndEachFailureSeparately() {
        DesignationResponse designation = createDesignation("BulkStatus");

        WorkerResponse succeeds = createWorker(designation.id());
        WorkerResponse alreadyInactive = createWorker(designation.id());
        workerService.updateStatus(alreadyInactive.id(), WorkerStatus.INACTIVE, "Pre-set for the test");
        UUID unknownId = UUID.randomUUID();

        BulkActionResult result = workerService.bulkUpdateStatus(
                Set.of(succeeds.id(), alreadyInactive.id(), unknownId), WorkerStatus.INACTIVE, "Bulk reason");

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(alreadyInactive.id(), unknownId);
        assertThat(result.failures()).filteredOn(f -> f.id().equals(alreadyInactive.id()))
                .extracting(BulkActionFailure::reason).first().satisfies(reason ->
                        assertThat((String) reason).containsIgnoringCase("already"));
        assertThat(result.failures()).filteredOn(f -> f.id().equals(unknownId))
                .extracting(BulkActionFailure::reason).containsExactly("Not found");

        // The one real success was actually applied, not merely counted.
        assertThat(workerService.get(succeeds.id()).status()).isEqualTo(WorkerStatus.INACTIVE);
    }

    // ---- exportToExcel ------------------------------------------------------------------------------

    @Test
    void exportToExcel_byIds_containsExpectedWorkerCodeAndColumns() throws IOException {
        DesignationResponse designation = createDesignation("ExportById");
        WorkerResponse worker = createWorker(designation.id());

        byte[] xlsxBytes = workerService.exportToExcel(Set.of(worker.id()), emptyCriteria());
        List<List<String>> rows = readExcelRows(xlsxBytes);

        assertThat(rows.get(0)).containsExactly("Worker Code", "First Name", "Last Name", "Mobile Number", "Status",
                "KYC Status", "Designation", "Current City", "Current State", "Permanent City",
                "Permanent State", "Face Enrolled");
        List<String> dataRow = rows.get(1);
        assertThat(dataRow.get(0)).isEqualTo(worker.workerCode());
        assertThat(dataRow.get(3)).isEqualTo(worker.mobileNumber());
        assertThat(dataRow.get(6)).isEqualTo(designation.name());
    }

    @Test
    void exportToExcel_withNoIds_fallsBackToFilterCriteria() throws IOException {
        DesignationResponse designation = createDesignation("ExportByFilter");
        WorkerResponse worker = createWorker(designation.id());

        byte[] xlsxBytes = workerService.exportToExcel(Set.of(),
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null));
        List<List<String>> rows = readExcelRows(xlsxBytes);

        assertThat(rows).anyMatch(row -> worker.workerCode().equals(row.get(0)));
    }

    @Test
    void exportToExcel_preservesCommasAndQuotesInCellValues() throws IOException {
        DesignationResponse designation = createDesignation("ExportQuoting");
        String trickyLastName = "Quoted \"Nickname\", Surname " + unique();
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, trickyLastName, null, Gender.MALE,
                null, uniqueMobile(), null, null, null, Set.of(designation.id()), designation.id(), Set.of()));

        byte[] xlsxBytes = workerService.exportToExcel(Set.of(worker.id()), emptyCriteria());
        List<List<String>> rows = readExcelRows(xlsxBytes);

        // Unlike CSV, a .xlsx cell needs no comma/quote escaping - the raw value is stored as-is in its own cell.
        assertThat(rows).anyMatch(row -> trickyLastName.equals(row.get(2)));
    }

    // ---- face enrollment data in listing/detail/export/filter ------------------------------------

    @Test
    void list_faceEnrolledAt_isNullBeforeEnrollmentAndSetAfter() {
        DesignationResponse designation = createDesignation("FaceEnrollList");
        WorkerResponse worker = createWorker(designation.id());

        PageResponse<WorkerSummaryResponse> beforeEnroll = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));
        assertThat(beforeEnroll.content()).hasSize(1);
        assertThat(beforeEnroll.content().get(0).id()).isEqualTo(worker.id());
        assertThat(beforeEnroll.content().get(0).faceEnrolledAt()).isNull();

        faceAuthService.enroll(worker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        PageResponse<WorkerSummaryResponse> afterEnroll = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));
        assertThat(afterEnroll.content()).hasSize(1);
        assertThat(afterEnroll.content().get(0).faceEnrolledAt()).isNotNull();
    }

    @Test
    void get_includesFullFaceEnrollmentDetailIncludingPhotoUrl() {
        DesignationResponse designation = createDesignation("FaceEnrollGet");
        WorkerResponse worker = createWorker(designation.id());

        assertThat(workerService.get(worker.id()).faceEnrollment().enrolled()).isFalse();

        faceAuthService.enroll(worker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        WorkerResponse refetched = workerService.get(worker.id());
        assertThat(refetched.faceEnrollment().enrolled()).isTrue();
        assertThat(refetched.faceEnrollment().enrolledAt()).isNotNull();
        assertThat(refetched.faceEnrollment().photoUrl()).isNotBlank();
    }

    @Test
    void exportToExcel_includesFaceEnrolledColumn() throws IOException {
        DesignationResponse designation = createDesignation("FaceEnrollExcel");
        WorkerResponse enrolledWorker = createWorker(designation.id());
        WorkerResponse unenrolledWorker = createWorker(designation.id());
        faceAuthService.enroll(enrolledWorker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        byte[] xlsxBytes = workerService.exportToExcel(Set.of(enrolledWorker.id(), unenrolledWorker.id()), emptyCriteria());
        List<List<String>> rows = readExcelRows(xlsxBytes);

        assertThat(rows.get(0)).containsExactly("Worker Code", "First Name", "Last Name", "Mobile Number", "Status",
                "KYC Status", "Designation", "Current City", "Current State", "Permanent City",
                "Permanent State", "Face Enrolled");
        assertThat(rows).anyMatch(row -> enrolledWorker.workerCode().equals(row.get(0)) && "Yes".equals(row.get(11)));
        assertThat(rows).anyMatch(row -> unenrolledWorker.workerCode().equals(row.get(0)) && "No".equals(row.get(11)));
    }

    @Test
    void list_filtersByFaceEnrolled_trueAndFalseEachMatchTheRightWorker() {
        DesignationResponse designation = createDesignation("FaceEnrollFilter");
        WorkerResponse enrolledWorker = createWorker(designation.id());
        WorkerResponse unenrolledWorker = createWorker(designation.id());
        faceAuthService.enroll(enrolledWorker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        PageResponse<WorkerSummaryResponse> enrolledOnly = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, true),
                PageRequest.of(0, 50));
        assertThat(enrolledOnly.content()).extracting(WorkerSummaryResponse::id).containsExactly(enrolledWorker.id());

        PageResponse<WorkerSummaryResponse> notEnrolledOnly = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, false),
                PageRequest.of(0, 50));
        assertThat(notEnrolledOnly.content()).extracting(WorkerSummaryResponse::id).containsExactly(unenrolledWorker.id());
    }

    @Test
    void list_filtersByFaceEnrolledTrue_whenNobodyIsEnrolled_returnsEmptyPage() {
        // Exercises buildPredicates' empty-id-set short-circuit for the "true" branch - must not try
        // to build an empty SQL IN() clause, must just return nothing.
        DesignationResponse designation = createDesignation("FaceEnrollNobody");
        createWorker(designation.id());

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, true),
                PageRequest.of(0, 50));

        assertThat(result.content()).isEmpty();
    }

    // ---- delete / bulkDelete --------------------------------------------------------------------

    @Test
    void delete_whileActive_isRejectedWithAClearMessage() {
        DesignationResponse designation = createDesignation("DeleteGuard");
        WorkerResponse worker = createWorker(designation.id());
        // REGISTERED -> INACTIVE -> ACTIVE: the only admin-reachable path to ACTIVE outside the real
        // KYC-verified flow (see WorkerServiceStatusGuardTest.updateStatus_toActive_fromInactive_...).
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "temp, for test setup");
        workerService.updateStatus(worker.id(), WorkerStatus.ACTIVE, null);

        assertThatThrownBy(() -> workerService.delete(worker.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ACTIVE");

        // Rejected, not silently skipped - the worker must still exist afterward.
        assertThat(workerService.get(worker.id()).status()).isEqualTo(WorkerStatus.ACTIVE);
    }

    @Test
    void delete_afterDeactivating_succeeds() {
        DesignationResponse designation = createDesignation("DeleteSucceeds");
        WorkerResponse worker = createWorker(designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        assertThatThrownBy(() -> workerService.get(worker.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bulkDelete_partialSuccess_activeWorkerBlocksOnlyItself() {
        DesignationResponse designation = createDesignation("BulkDelete");

        WorkerResponse deletable = createWorker(designation.id());
        workerService.updateStatus(deletable.id(), WorkerStatus.INACTIVE, "Leaving");

        WorkerResponse activeWorker = createWorker(designation.id());
        workerService.updateStatus(activeWorker.id(), WorkerStatus.INACTIVE, "temp, for test setup");
        workerService.updateStatus(activeWorker.id(), WorkerStatus.ACTIVE, null);

        UUID unknownId = UUID.randomUUID();

        BulkActionResult result = workerService.bulkDelete(Set.of(deletable.id(), activeWorker.id(), unknownId));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures()).extracting(BulkActionFailure::id)
                .containsExactlyInAnyOrder(activeWorker.id(), unknownId);

        assertThatThrownBy(() -> workerService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        // The failed id's own guard check must not affect the other ids in the same batch - a plain
        // shared @Transactional is safe here since a soft-delete is just an UPDATE (see
        // WorkerService.bulkDelete's Javadoc for why this no longer needs REQUIRES_NEW).
        assertThat(workerService.get(activeWorker.id()).status()).isEqualTo(WorkerStatus.ACTIVE);
    }

    @Test
    void delete_softDeletesWorker_disappearsFromListToo() {
        DesignationResponse designation = createDesignation("DeleteFromList");
        WorkerResponse worker = createWorker(designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        // Not just get() by id - the Specification/Criteria-based list() path is a genuinely
        // different query mechanism and needs its own proof that @SQLRestriction applies there too.
        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 50));
        assertThat(result.content()).extracting(WorkerSummaryResponse::id).doesNotContain(worker.id());
    }

    @Test
    void delete_preservesStatusHistory_ratherThanCascadingItAway() {
        DesignationResponse designation = createDesignation("DeletePreserves");
        WorkerResponse worker = createWorker(designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        // Bypasses WorkerService (whose listStatusHistory() 404s for a deleted worker, correctly -
        // see its existsById check) to query the repository directly: proves the rows are still
        // physically in the database, not cascaded away by the old hard-delete's ON DELETE CASCADE.
        assertThat(statusHistoryRepository.findByWorkerIdOrderByCreatedAtDesc(worker.id(), PageRequest.of(0, 50)))
                .isNotEmpty();
    }

    /**
     * Proves {@code WorkerService.delete} calls {@code FaceAuthService.excludeFromMatching} (added
     * 2026-11-17 alongside enrollment-time duplicate detection and face identify's eligibility
     * filtering) - the row and its photo are still there (see the class/method-level Javadoc on why
     * nothing is cascaded away), only {@code excludedFromMatchingAt} changes.
     */
    @Test
    void delete_excludesFaceEnrollmentFromMatching_butPreservesTheRowItself() {
        DesignationResponse designation = createDesignation("DeleteExcludesFace");
        WorkerResponse worker = createWorker(designation.id());
        faceAuthService.enroll(worker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");
        assertThat(faceEnrollmentRepository.findByWorkerId(worker.id()).orElseThrow().getExcludedFromMatchingAt()).isNull();
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        assertThat(faceEnrollmentRepository.findByWorkerId(worker.id()).orElseThrow().getExcludedFromMatchingAt()).isNotNull();
        assertThat(faceAuthService.getEnrollment(worker.id()).enrolled()).isTrue();
    }

    @Test
    void delete_whenNothingWasEverFaceEnrolled_excludeFromMatchingIsANoOp() {
        DesignationResponse designation = createDesignation("DeleteNoFaceEnrollment");
        WorkerResponse worker = createWorker(designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        assertThatCode(() -> workerService.delete(worker.id())).doesNotThrowAnyException();
    }

    @Test
    void delete_thenMobileNumberBecomesAvailableForANewWorker() {
        DesignationResponse designation = createDesignation("DeleteFreesNumber");
        String mobileNumber = uniqueMobile();
        WorkerResponse original = workerService.create(new CreateWorkerRequest("Test", null, "Original " + unique(), null,
                Gender.MALE, null, mobileNumber, null, null, null, Set.of(designation.id()), designation.id(), Set.of()));
        workerService.updateStatus(original.id(), WorkerStatus.INACTIVE, "Leaving the platform");
        workerService.delete(original.id());

        WorkerResponse replacement = workerService.create(new CreateWorkerRequest("Test", null, "Replacement " + unique(), null,
                Gender.MALE, null, mobileNumber, null, null, null, Set.of(designation.id()), designation.id(), Set.of()));

        assertThat(replacement.mobileNumber()).isEqualTo(mobileNumber);
        assertThat(replacement.id()).isNotEqualTo(original.id());
    }

    // ---- scoped filter options (item 4: "only offer values actually in use") ----------------------

    @Test
    void filterOptions_onlyReturnsIndustriesDesignationsAndSkillsActuallyAssignedToAWorker() {
        DesignationResponse usedDesignation = createDesignation("FilterOptsUsed");
        DesignationResponse unusedDesignation = createDesignation("FilterOptsUnused");
        SkillResponse usedSkill = skillService.create(new CreateSkillRequest("Used Skill " + unique(), usedDesignation.id()));
        SkillResponse unusedSkill = skillService.create(new CreateSkillRequest("Unused Skill " + unique(), usedDesignation.id()));

        workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE, null,
                uniqueMobile(), null, null, null,
                Set.of(usedDesignation.id()), usedDesignation.id(), Set.of(usedSkill.id())));

        WorkerFilterOptionsResponse options = filterOptionsService.get();

        assertThat(options.designations()).extracting(d -> d.id()).contains(usedDesignation.id());
        assertThat(options.designations()).extracting(d -> d.id()).doesNotContain(unusedDesignation.id());
        assertThat(options.skills()).extracting(s -> s.id()).contains(usedSkill.id());
        assertThat(options.skills()).extracting(s -> s.id()).doesNotContain(unusedSkill.id());
    }

    @Test
    void filterOptions_includesStateAndCityOfAWorkersAddress() {
        DesignationResponse designation = createDesignation("FilterOptsAddress");
        StateCity stateCity = seededStateCitiesWithCities(1).get(0);
        WorkerResponse worker = createWorker(designation.id());
        addressService.update(worker.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("1 Filter Opts St", null, stateCity.stateId(), stateCity.cityId(), "100001"));

        WorkerFilterOptionsResponse options = filterOptionsService.get();

        assertThat(options.states()).extracting(s -> s.id()).contains(stateCity.stateId());
        assertThat(options.cities()).extracting(c -> c.id()).contains(stateCity.cityId());
    }

    @Test
    void filterOptions_excludesAnIndustryOnlyReferencedByASoftDeletedWorker() {
        DesignationResponse designation = createDesignation("FilterOptsDeleted");
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("FilterOptsDeletedIndustry " + unique()));
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), null,
                Gender.MALE, null, uniqueMobile(), null, industry.id(), null,
                Set.of(designation.id()), designation.id(), Set.of()));
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");
        workerService.delete(worker.id());

        WorkerFilterOptionsResponse options = filterOptionsService.get();

        assertThat(options.industries()).extracting(i -> i.id()).doesNotContain(industry.id());
    }
}
