package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.application.WorkerSearchCriteria;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the listing-API enhancement added on top of {@link WorkerService}'s original CRUD/status
 * surface (see {@link WorkerServiceIntegrationTest} for that original coverage, left untouched):
 * multiselect list filters, {@code bulkUpdateStatus}, {@code exportToCsv}, and single/bulk
 * {@code delete} - a soft delete (sets {@code deleted_at}, guarded by the worker being non-ACTIVE)
 * that preserves every satellite row and frees the worker's mobile number for reuse; see
 * {@code Worker}'s class-level Javadoc and {@code WorkerService.delete()}'s Javadoc for the full
 * mechanism. Kept in its own file rather than growing
 * {@link WorkerServiceIntegrationTest} further, matching this codebase's existing split between that
 * file and {@link WorkerSatelliteServicesIntegrationTest}/{@link WorkerKycVerificationFlowIntegrationTest}.
 *
 * <p>Every assertion below either filters by a freshly-generated, globally-unique id (department/
 * designation/mobile number) or scopes its {@code Set.of(...)} to ids it just created - never an
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
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private WorkerStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private FaceAuthService faceAuthService;

    private static final byte[] FAKE_JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5
    };

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private DepartmentResponse createDepartment() {
        return departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
    }

    private DesignationResponse createDesignation(String label) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest(label + " Industry " + unique()));
        return designationService.create(new CreateDesignationRequest(label + " " + unique(), Set.of(industry.id()), null));
    }

    private WorkerResponse createWorker(DepartmentResponse department, UUID designationId) {
        return workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE, null,
                uniqueMobile(), null, department.id(), null, Set.of(designationId), designationId, Set.of()));
    }

    private static WorkerSearchCriteria emptyCriteria() {
        return new WorkerSearchCriteria(null, null, null, null, null, null, null);
    }

    // ---- Multiselect filters ------------------------------------------------------------------

    @Test
    void list_filtersByMultipleDesignationIds_matchesWorkersUnderEither() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designationA = createDesignation("MultiDesigA");
        DesignationResponse designationB = createDesignation("MultiDesigB");
        DesignationResponse designationC = createDesignation("MultiDesigC");

        WorkerResponse workerA = createWorker(department, designationA.id());
        WorkerResponse workerB = createWorker(department, designationB.id());
        WorkerResponse workerC = createWorker(department, designationC.id());

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designationA.id(), designationB.id()), null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id)
                .containsExactlyInAnyOrder(workerA.id(), workerB.id());
        assertThat(result.content()).extracting(WorkerSummaryResponse::id).doesNotContain(workerC.id());
    }

    @Test
    void list_filtersByMultipleStatuses_matchesWorkersInEither() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("MultiStatus");

        WorkerResponse registeredWorker = createWorker(department, designation.id());
        WorkerResponse inactiveWorker = createWorker(department, designation.id());
        workerService.updateStatus(inactiveWorker.id(), WorkerStatus.INACTIVE, "Testing multiselect status filter");

        // Scoped to this test's own fresh designation id so accumulated data from other tests/runs
        // can never leak into the result set (gotcha: never trust an otherwise-unfiltered list()).
        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.REGISTERED, WorkerStatus.INACTIVE), null, null,
                        Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id)
                .containsExactlyInAnyOrder(registeredWorker.id(), inactiveWorker.id());
    }

    // ---- search ---------------------------------------------------------------------------------

    @Test
    void list_searchMatchesPersonalEmail() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("SearchEmail");
        String email = "search" + unique() + "@example.com";
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), email, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, email),
                PageRequest.of(0, 50));

        assertThat(result.content()).extracting(WorkerSummaryResponse::id).containsExactly(worker.id());
    }

    // ---- sorting --------------------------------------------------------------------------------

    @Test
    void list_sortsByLastNameAscendingOrDescending() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("SortWorker");
        String prefix = "SortW" + unique();
        WorkerResponse beta = workerService.create(new CreateWorkerRequest("Test", null, prefix + " Beta", Gender.MALE,
                null, uniqueMobile(), null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));
        WorkerResponse alpha = workerService.create(new CreateWorkerRequest("Test", null, prefix + " Alpha", Gender.MALE,
                null, uniqueMobile(), null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> ascending = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 100, Sort.by("lastName").ascending()));
        assertThat(ascending.content()).extracting(WorkerSummaryResponse::id).containsExactly(alpha.id(), beta.id());

        PageResponse<WorkerSummaryResponse> descending = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null),
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
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("BulkStatus");

        WorkerResponse succeeds = createWorker(department, designation.id());
        WorkerResponse alreadyInactive = createWorker(department, designation.id());
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

    // ---- exportToCsv ------------------------------------------------------------------------------

    @Test
    void exportToCsv_byIds_containsExpectedWorkerCodeAndColumns() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("ExportById");
        WorkerResponse worker = createWorker(department, designation.id());

        byte[] csvBytes = workerService.exportToCsv(Set.of(worker.id()), emptyCriteria());
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("Worker Code,First Name,Last Name,Mobile Number,Status,KYC Status,Designation,Department");
        assertThat(csv).contains(worker.workerCode());
        assertThat(csv).contains(worker.mobileNumber());
        assertThat(csv).contains(designation.name());
        assertThat(csv).contains(department.name());
    }

    @Test
    void exportToCsv_withNoIds_fallsBackToFilterCriteria() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("ExportByFilter");
        WorkerResponse worker = createWorker(department, designation.id());

        byte[] csvBytes = workerService.exportToCsv(Set.of(),
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).contains(worker.workerCode());
    }

    @Test
    void exportToCsv_quotesFieldsContainingACommaOrQuote() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("ExportQuoting");
        String trickyLastName = "Quoted \"Nickname\", Surname " + unique();
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, trickyLastName, Gender.MALE,
                null, uniqueMobile(), null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));

        byte[] csvBytes = workerService.exportToCsv(Set.of(worker.id()), emptyCriteria());
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // RFC 4180: the whole field is wrapped in quotes, and any literal quote inside it is doubled.
        String expectedEscaped = "\"" + trickyLastName.replace("\"", "\"\"") + "\"";
        assertThat(csv).contains(expectedEscaped);
    }

    // ---- face enrollment data in listing/detail/export ------------------------------------------

    @Test
    void list_faceEnrolledAt_isNullBeforeEnrollmentAndSetAfter() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("FaceEnrollList");
        WorkerResponse worker = createWorker(department, designation.id());

        PageResponse<WorkerSummaryResponse> beforeEnroll = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 50));
        assertThat(beforeEnroll.content()).hasSize(1);
        assertThat(beforeEnroll.content().get(0).id()).isEqualTo(worker.id());
        assertThat(beforeEnroll.content().get(0).faceEnrolledAt()).isNull();

        faceAuthService.enroll(worker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        PageResponse<WorkerSummaryResponse> afterEnroll = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 50));
        assertThat(afterEnroll.content()).hasSize(1);
        assertThat(afterEnroll.content().get(0).faceEnrolledAt()).isNotNull();
    }

    @Test
    void get_includesFullFaceEnrollmentDetailIncludingPhotoUrl() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("FaceEnrollGet");
        WorkerResponse worker = createWorker(department, designation.id());

        assertThat(workerService.get(worker.id()).faceEnrollment().enrolled()).isFalse();

        faceAuthService.enroll(worker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        WorkerResponse refetched = workerService.get(worker.id());
        assertThat(refetched.faceEnrollment().enrolled()).isTrue();
        assertThat(refetched.faceEnrollment().enrolledAt()).isNotNull();
        assertThat(refetched.faceEnrollment().photoUrl()).isNotBlank();
    }

    @Test
    void exportToCsv_includesFaceEnrolledColumn() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("FaceEnrollCsv");
        WorkerResponse enrolledWorker = createWorker(department, designation.id());
        WorkerResponse unenrolledWorker = createWorker(department, designation.id());
        faceAuthService.enroll(enrolledWorker.id(), "face.jpg", "image/jpeg", FAKE_JPEG_BYTES, true, "127.0.0.1");

        byte[] csvBytes = workerService.exportToCsv(Set.of(enrolledWorker.id(), unenrolledWorker.id()), emptyCriteria());
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        List<String> lines = List.of(csv.split("\r\n"));

        assertThat(lines.get(0)).isEqualTo(
                "Worker Code,First Name,Last Name,Mobile Number,Status,KYC Status,Designation,Department,Face Enrolled");
        assertThat(lines).anyMatch(line -> line.startsWith(enrolledWorker.workerCode()) && line.endsWith(",Yes"));
        assertThat(lines).anyMatch(line -> line.startsWith(unenrolledWorker.workerCode()) && line.endsWith(",No"));
    }

    // ---- delete / bulkDelete --------------------------------------------------------------------

    @Test
    void delete_whileActive_isRejectedWithAClearMessage() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("DeleteGuard");
        WorkerResponse worker = createWorker(department, designation.id());
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
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("DeleteSucceeds");
        WorkerResponse worker = createWorker(department, designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        assertThatThrownBy(() -> workerService.get(worker.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void bulkDelete_partialSuccess_activeWorkerBlocksOnlyItself() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("BulkDelete");

        WorkerResponse deletable = createWorker(department, designation.id());
        workerService.updateStatus(deletable.id(), WorkerStatus.INACTIVE, "Leaving");

        WorkerResponse activeWorker = createWorker(department, designation.id());
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
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("DeleteFromList");
        WorkerResponse worker = createWorker(department, designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        // Not just get() by id - the Specification/Criteria-based list() path is a genuinely
        // different query mechanism and needs its own proof that @SQLRestriction applies there too.
        PageResponse<WorkerSummaryResponse> result = workerService.list(
                new WorkerSearchCriteria(null, null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 50));
        assertThat(result.content()).extracting(WorkerSummaryResponse::id).doesNotContain(worker.id());
    }

    @Test
    void delete_preservesStatusHistory_ratherThanCascadingItAway() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("DeletePreserves");
        WorkerResponse worker = createWorker(department, designation.id());
        workerService.updateStatus(worker.id(), WorkerStatus.INACTIVE, "Leaving the platform");

        workerService.delete(worker.id());

        // Bypasses WorkerService (whose listStatusHistory() 404s for a deleted worker, correctly -
        // see its existsById check) to query the repository directly: proves the rows are still
        // physically in the database, not cascaded away by the old hard-delete's ON DELETE CASCADE.
        assertThat(statusHistoryRepository.findByWorkerIdOrderByCreatedAtDesc(worker.id(), PageRequest.of(0, 50)))
                .isNotEmpty();
    }

    @Test
    void delete_thenMobileNumberBecomesAvailableForANewWorker() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("DeleteFreesNumber");
        String mobileNumber = uniqueMobile();
        WorkerResponse original = workerService.create(new CreateWorkerRequest("Test", null, "Original " + unique(),
                Gender.MALE, null, mobileNumber, null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));
        workerService.updateStatus(original.id(), WorkerStatus.INACTIVE, "Leaving the platform");
        workerService.delete(original.id());

        WorkerResponse replacement = workerService.create(new CreateWorkerRequest("Test", null, "Replacement " + unique(),
                Gender.MALE, null, mobileNumber, null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));

        assertThat(replacement.mobileNumber()).isEqualTo(mobileNumber);
        assertThat(replacement.id()).isNotEqualTo(original.id());
    }
}
