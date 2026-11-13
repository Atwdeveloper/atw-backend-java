package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillService;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerDesignationsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerSkillsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatusHistoryResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.domain.Gender;
import in.anytimework.atwbackend.worker.domain.WorkerDesignation;
import in.anytimework.atwbackend.worker.domain.WorkerDesignationRepository;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import in.anytimework.atwbackend.worker.application.WorkerSearchCriteria;
import in.anytimework.atwbackend.worker.application.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for worker create/update/designation-skill assignment/status/search against the real local test Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class WorkerServiceIntegrationTest {

    @Autowired
    private WorkerService workerService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private WorkerDesignationRepository workerDesignationRepository;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private DepartmentResponse createDepartment() {
        return departmentService.create(new CreateDepartmentRequest("Housekeeping " + unique()));
    }

    private DesignationResponse createDesignation(String label) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest(label + " Industry " + unique()));
        return designationService.create(new CreateDesignationRequest(label + " " + unique(), Set.of(industry.id()), null));
    }

    private SkillResponse createSkill(UUID designationId, String label) {
        return skillService.create(new CreateSkillRequest(label + " " + unique(), designationId));
    }

    private CreateWorkerRequest baseRequest(DepartmentResponse department, UUID designationId) {
        return new CreateWorkerRequest("Ramesh", null, "Kumar " + unique(), Gender.MALE, null,
                uniqueMobile(), null, department.id(), null, Set.of(designationId), designationId, Set.of());
    }

    @Test
    void create_thenGet_roundTrips() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Electrician");
        SkillResponse skill = createSkill(designation.id(), "House Wiring");

        CreateWorkerRequest request = new CreateWorkerRequest("Ramesh", "Kumar", "Yadav " + unique(), Gender.MALE,
                null, uniqueMobile(), "ramesh@example.com", department.id(), null,
                Set.of(designation.id()), designation.id(), Set.of(skill.id()));

        WorkerResponse created = workerService.create(request);

        assertThat(created.workerCode()).matches("WRK\\d{7}");
        assertThat(created.status()).isEqualTo(WorkerStatus.REGISTERED);
        assertThat(created.kycStatus()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(created.designations()).hasSize(1);
        assertThat(created.designations().get(0).primary()).isTrue();
        assertThat(created.skills()).extracting(s -> s.id()).containsExactly(skill.id());

        WorkerResponse fetched = workerService.get(created.id());
        assertThat(fetched.mobileNumber()).isEqualTo(request.mobileNumber());
    }

    @Test
    void create_withDuplicateMobile_isRejected() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Plumber");
        CreateWorkerRequest request = baseRequest(department, designation.id());
        workerService.create(request);

        CreateWorkerRequest duplicate = new CreateWorkerRequest("Other", null, "Worker " + unique(), Gender.MALE,
                null, request.mobileNumber(), null, department.id(), null,
                Set.of(designation.id()), designation.id(), Set.of());

        assertThatThrownBy(() -> workerService.create(duplicate)).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withMoreThanThreeDesignations_isRejected() {
        DepartmentResponse department = createDepartment();
        UUID d1 = createDesignation("D1").id();
        UUID d2 = createDesignation("D2").id();
        UUID d3 = createDesignation("D3").id();
        UUID d4 = createDesignation("D4").id();

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), null, department.id(), null, Set.of(d1, d2, d3, d4), d1, Set.of());

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void create_withPrimaryNotInDesignationSet_isRejected() {
        DepartmentResponse department = createDepartment();
        UUID d1 = createDesignation("D1").id();
        UUID other = createDesignation("Other").id();

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), null, department.id(), null, Set.of(d1), other, Set.of());

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("primaryDesignationId");
    }

    @Test
    void create_withSkillNotBelongingToSelectedDesignation_isRejected() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designationA = createDesignation("Mason");
        DesignationResponse designationB = createDesignation("Carpenter");
        SkillResponse skillUnderB = createSkill(designationB.id(), "Furniture Making");

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), null, department.id(), null,
                Set.of(designationA.id()), designationA.id(), Set.of(skillUnderB.id()));

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void create_withUnknownDepartment_isRejected() {
        DesignationResponse designation = createDesignation("Driver");
        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), null, UUID.randomUUID(), null, Set.of(designation.id()), designation.id(), Set.of());

        assertThatThrownBy(() -> workerService.create(request)).isInstanceOf(ValidationException.class);
    }

    @Test
    void update_changesProfileFieldsWithoutTouchingDesignations() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Welder");
        WorkerResponse created = workerService.create(baseRequest(department, designation.id()));

        String newMobile = uniqueMobile();
        UpdateWorkerRequest update = new UpdateWorkerRequest("Updated", "Name", created.lastName(), Gender.FEMALE,
                null, newMobile, "updated@example.com", department.id(), null);
        WorkerResponse updated = workerService.update(created.id(), update);

        assertThat(updated.firstName()).isEqualTo("Updated");
        assertThat(updated.mobileNumber()).isEqualTo(newMobile);
        assertThat(updated.designations()).hasSize(1);
    }

    @Test
    void assignDesignations_replacesAndDropsSkillsThatNoLongerBelong() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designationA = createDesignation("Fitter");
        SkillResponse skillUnderA = createSkill(designationA.id(), "Pipe Fitting");
        DesignationResponse designationB = createDesignation("Rigger");

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE,
                null, uniqueMobile(), null, department.id(), null,
                Set.of(designationA.id()), designationA.id(), Set.of(skillUnderA.id()));
        WorkerResponse created = workerService.create(request);
        assertThat(created.skills()).hasSize(1);

        WorkerResponse reassigned = workerService.assignDesignations(created.id(),
                new AssignWorkerDesignationsRequest(Set.of(designationB.id()), designationB.id()));

        assertThat(reassigned.designations()).extracting(d -> d.id()).containsExactly(designationB.id());
        assertThat(reassigned.skills()).isEmpty();
    }

    @Test
    void assignSkills_withSkillNotBelongingToCurrentDesignations_isRejected() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Painter");
        DesignationResponse otherDesignation = createDesignation("Tiler");
        SkillResponse foreignSkill = createSkill(otherDesignation.id(), "Tile Laying");
        WorkerResponse created = workerService.create(baseRequest(department, designation.id()));

        assertThatThrownBy(() -> workerService.assignSkills(created.id(), new AssignWorkerSkillsRequest(Set.of(foreignSkill.id()))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateStatus_recordsTransitionHistoryWithReason() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Security Guard");
        WorkerResponse created = workerService.create(baseRequest(department, designation.id()));

        workerService.updateStatus(created.id(), WorkerStatus.INACTIVE, "Left voluntarily");

        PageResponse<WorkerStatusHistoryResponse> history = workerService.listStatusHistory(created.id(), PageRequest.of(0, 10));
        assertThat(history.content()).hasSize(2);
        assertThat(history.content().get(0).toStatus()).isEqualTo(WorkerStatus.INACTIVE);
        assertThat(history.content().get(0).reason()).isEqualTo("Left voluntarily");
        assertThat(history.content().get(1).fromStatus()).isNull();
        assertThat(history.content().get(1).toStatus()).isEqualTo(WorkerStatus.REGISTERED);
    }

    @Test
    void list_filtersByStatusAndDesignationAndSearchTerm() {
        DepartmentResponse department = createDepartment();
        DesignationResponse designation = createDesignation("Cook " + unique());
        String findableFirstName = "Findme" + unique();
        WorkerResponse created = workerService.create(
                new CreateWorkerRequest(findableFirstName, null, "Unique" + unique(), Gender.MALE, null,
                        uniqueMobile(), null, department.id(), null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> byStatus = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.REGISTERED), null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 20));
        assertThat(byStatus.content()).extracting(WorkerSummaryResponse::id).contains(created.id());
        assertThat(byStatus.content()).allSatisfy(w -> assertThat(w.primaryDesignationName()).isEqualTo(designation.name()));

        // Searches by this test's own unique first name, not the bare literal "Findme" - this test
        // database accumulates rows across every run in a session, so an unscoped, unsorted page of 20
        // can't be assumed to still include this run's row once enough prior runs share the same term.
        PageResponse<WorkerSummaryResponse> bySearch = workerService.list(
                new WorkerSearchCriteria(null, null, null, null, null, null, findableFirstName),
                PageRequest.of(0, 20));
        assertThat(bySearch.content()).extracting(WorkerSummaryResponse::id).contains(created.id());

        PageResponse<WorkerSummaryResponse> byWrongStatus = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.BLACKLISTED), null, null, Set.of(designation.id()), null, null, null),
                PageRequest.of(0, 20));
        assertThat(byWrongStatus.content()).extracting(WorkerSummaryResponse::id).doesNotContain(created.id());
    }

    @Test
    void worker_designation_primaryUniqueness_isEnforcedByTheDatabaseItself() {
        // Bypasses WorkerService's own validation entirely, to prove uq_worker_designation_primary is
        // a real DB-level guarantee - not merely an application-level check that a second, careless
        // write path could silently violate.
        DepartmentResponse department = createDepartment();
        DesignationResponse designationA = createDesignation("Guard A");
        DesignationResponse designationB = createDesignation("Guard B");
        WorkerResponse created = workerService.create(baseRequest(department, designationA.id()));

        WorkerDesignation secondPrimary = new WorkerDesignation(created.id(), designationB.id(), true);
        assertThatThrownBy(() -> workerDesignationRepository.saveAndFlush(secondPrimary))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
