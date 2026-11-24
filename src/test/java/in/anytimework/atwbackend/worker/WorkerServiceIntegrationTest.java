package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.kyc.application.KycStatus;
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
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerDesignationsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerSkillsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerAddressRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatusHistoryResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.application.WorkerAddressService;
import in.anytimework.atwbackend.worker.domain.AddressType;
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
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private WorkerDesignationRepository workerDesignationRepository;
    @Autowired
    private WorkerAddressService addressService;
    @Autowired
    private GeographyLookupService geography;

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

    private SkillResponse createSkill(UUID designationId, String label) {
        return skillService.create(new CreateSkillRequest(label + " " + unique(), designationId));
    }

    private CreateWorkerRequest baseRequest(UUID designationId) {
        return new CreateWorkerRequest("Ramesh", null, "Kumar " + unique(), null, Gender.MALE, null,
                uniqueMobile(), null, null, null, Set.of(designationId), designationId, Set.of());
    }

    @Test
    void create_thenGet_roundTrips() {
        DesignationResponse designation = createDesignation("Electrician");
        SkillResponse skill = createSkill(designation.id(), "House Wiring");

        CreateWorkerRequest request = new CreateWorkerRequest("Ramesh", "Kumar", "Yadav " + unique(), null, Gender.MALE,
                null, uniqueMobile(), "ramesh@example.com", null, null,
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
        DesignationResponse designation = createDesignation("Plumber");
        CreateWorkerRequest request = baseRequest(designation.id());
        workerService.create(request);

        CreateWorkerRequest duplicate = new CreateWorkerRequest("Other", null, "Worker " + unique(), null, Gender.MALE,
                null, request.mobileNumber(), null, null, null,
                Set.of(designation.id()), designation.id(), Set.of());

        assertThatThrownBy(() -> workerService.create(duplicate)).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withMoreThanThreeDesignations_isRejected() {
        UUID d1 = createDesignation("D1").id();
        UUID d2 = createDesignation("D2").id();
        UUID d3 = createDesignation("D3").id();
        UUID d4 = createDesignation("D4").id();

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE,
                null, uniqueMobile(), null, null, null, Set.of(d1, d2, d3, d4), d1, Set.of());

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void create_withPrimaryNotInDesignationSet_isRejected() {
        UUID d1 = createDesignation("D1").id();
        UUID other = createDesignation("Other").id();

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE,
                null, uniqueMobile(), null, null, null, Set.of(d1), other, Set.of());

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("primaryDesignationId");
    }

    @Test
    void create_withSkillNotBelongingToSelectedDesignation_isRejected() {
        DesignationResponse designationA = createDesignation("Mason");
        DesignationResponse designationB = createDesignation("Carpenter");
        SkillResponse skillUnderB = createSkill(designationB.id(), "Furniture Making");

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE,
                null, uniqueMobile(), null, null, null,
                Set.of(designationA.id()), designationA.id(), Set.of(skillUnderB.id()));

        assertThatThrownBy(() -> workerService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void create_withFatherNameAndWorkExperience_roundTrips() {
        DesignationResponse designation = createDesignation("Experienced");
        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), "S/O: Suresh Kumar",
                Gender.MALE, null, uniqueMobile(), null, null, 5,
                Set.of(designation.id()), designation.id(), Set.of());

        WorkerResponse created = workerService.create(request);

        assertThat(created.fatherName()).isEqualTo("S/O: Suresh Kumar");
        assertThat(created.workExperienceYears()).isEqualTo(5);

        WorkerResponse fetched = workerService.get(created.id());
        assertThat(fetched.fatherName()).isEqualTo("S/O: Suresh Kumar");
        assertThat(fetched.workExperienceYears()).isEqualTo(5);
    }

    @Test
    void update_changesFatherNameAndWorkExperience() {
        DesignationResponse designation = createDesignation("UpdateExperience");
        WorkerResponse created = workerService.create(baseRequest(designation.id()));
        assertThat(created.workExperienceYears()).isNull();

        UpdateWorkerRequest update = new UpdateWorkerRequest(created.firstName(), created.middleName(),
                created.lastName(), "D/O: Priya Sharma", Gender.FEMALE, null, created.mobileNumber(),
                null, null, 3);
        WorkerResponse updated = workerService.update(created.id(), update);

        assertThat(updated.fatherName()).isEqualTo("D/O: Priya Sharma");
        assertThat(updated.workExperienceYears()).isEqualTo(3);
    }

    @Test
    void get_includesFullCurrentAndPermanentAddressResolvedToNames() {
        DesignationResponse designation = createDesignation("AddressDetail");
        WorkerResponse created = workerService.create(baseRequest(designation.id()));
        UUID stateId = geography.listStates().get(0).id();

        addressService.update(created.id(), AddressType.CURRENT,
                new UpdateWorkerAddressRequest("Worker Hostel", "Sector 18", stateId, null, "122001"));

        WorkerResponse fetched = workerService.get(created.id());

        assertThat(fetched.currentAddress()).isNotNull();
        assertThat(fetched.currentAddress().line1()).isEqualTo("Worker Hostel");
        assertThat(fetched.currentAddress().line2()).isEqualTo("Sector 18");
        assertThat(fetched.currentAddress().pincode()).isEqualTo("122001");
        assertThat(fetched.currentAddress().state()).isEqualTo(geography.listStates().get(0).name());
        assertThat(fetched.permanentAddress()).isNull();
    }

    @Test
    void update_changesProfileFieldsWithoutTouchingDesignations() {
        DesignationResponse designation = createDesignation("Welder");
        WorkerResponse created = workerService.create(baseRequest(designation.id()));

        String newMobile = uniqueMobile();
        UpdateWorkerRequest update = new UpdateWorkerRequest("Updated", "Name", created.lastName(), null, Gender.FEMALE,
                null, newMobile, "updated@example.com", null, null);
        WorkerResponse updated = workerService.update(created.id(), update);

        assertThat(updated.firstName()).isEqualTo("Updated");
        assertThat(updated.mobileNumber()).isEqualTo(newMobile);
        assertThat(updated.designations()).hasSize(1);
    }

    @Test
    void assignDesignations_replacesAndDropsSkillsThatNoLongerBelong() {
        DesignationResponse designationA = createDesignation("Fitter");
        SkillResponse skillUnderA = createSkill(designationA.id(), "Pipe Fitting");
        DesignationResponse designationB = createDesignation("Rigger");

        CreateWorkerRequest request = new CreateWorkerRequest("Test", null, "Worker " + unique(), null, Gender.MALE,
                null, uniqueMobile(), null, null, null,
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
        DesignationResponse designation = createDesignation("Painter");
        DesignationResponse otherDesignation = createDesignation("Tiler");
        SkillResponse foreignSkill = createSkill(otherDesignation.id(), "Tile Laying");
        WorkerResponse created = workerService.create(baseRequest(designation.id()));

        assertThatThrownBy(() -> workerService.assignSkills(created.id(), new AssignWorkerSkillsRequest(Set.of(foreignSkill.id()))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateStatus_recordsTransitionHistoryWithReason() {
        DesignationResponse designation = createDesignation("Security Guard");
        WorkerResponse created = workerService.create(baseRequest(designation.id()));

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
        DesignationResponse designation = createDesignation("Cook " + unique());
        String findableFirstName = "Findme" + unique();
        WorkerResponse created = workerService.create(
                new CreateWorkerRequest(findableFirstName, null, "Unique" + unique(), null, Gender.MALE, null,
                        uniqueMobile(), null, null, null, Set.of(designation.id()), designation.id(), Set.of()));

        PageResponse<WorkerSummaryResponse> byStatus = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.REGISTERED), null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(byStatus.content()).extracting(WorkerSummaryResponse::id).contains(created.id());
        assertThat(byStatus.content()).allSatisfy(w -> assertThat(w.primaryDesignationName()).isEqualTo(designation.name()));

        // Searches by this test's own unique first name, not the bare literal "Findme" - this test
        // database accumulates rows across every run in a session, so an unscoped, unsorted page of 20
        // can't be assumed to still include this run's row once enough prior runs share the same term.
        PageResponse<WorkerSummaryResponse> bySearch = workerService.list(
                new WorkerSearchCriteria(null, null, null, null, null, findableFirstName, null, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(bySearch.content()).extracting(WorkerSummaryResponse::id).contains(created.id());

        PageResponse<WorkerSummaryResponse> byWrongStatus = workerService.list(
                new WorkerSearchCriteria(Set.of(WorkerStatus.BLACKLISTED), null, Set.of(designation.id()), null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20));
        assertThat(byWrongStatus.content()).extracting(WorkerSummaryResponse::id).doesNotContain(created.id());
    }

    @Test
    void worker_designation_primaryUniqueness_isEnforcedByTheDatabaseItself() {
        // Bypasses WorkerService's own validation entirely, to prove uq_worker_designation_primary is
        // a real DB-level guarantee - not merely an application-level check that a second, careless
        // write path could silently violate.
        DesignationResponse designationA = createDesignation("Guard A");
        DesignationResponse designationB = createDesignation("Guard B");
        WorkerResponse created = workerService.create(baseRequest(designationA.id()));

        WorkerDesignation secondPrimary = new WorkerDesignation(created.id(), designationB.id(), true);
        assertThatThrownBy(() -> workerDesignationRepository.saveAndFlush(secondPrimary))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
