package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.SkillService;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for skill CRUD + icon upload against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class SkillAdminIntegrationTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    @Autowired
    private SkillService skillService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private WorkerService workerService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private DesignationResponse createDesignation(String label) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest(label + " Industry " + unique()));
        return designationService.create(new CreateDesignationRequest(label + " " + unique(), Set.of(industry.id()), null));
    }

    /** A worker holding {@code skillId} (under {@code designationId}) - exists purely to exercise the delete-guard tests below. */
    private void createWorkerWithSkill(UUID designationId, UUID skillId) {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
        workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE, null,
                uniqueMobile(), null, department.id(), null, Set.of(designationId), designationId, Set.of(skillId)));
    }

    @Test
    void create_thenGet_roundTrips() {
        DesignationResponse designation = createDesignation("Warehouse Helper");

        SkillResponse created = skillService.create(new CreateSkillRequest("Forklift Operation " + unique(), designation.id()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.designationId()).isEqualTo(designation.id());
        assertThat(created.designationName()).isEqualTo(designation.name());
        SkillResponse fetched = skillService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
        assertThat(fetched.designationName()).isEqualTo(designation.name());
    }

    @Test
    void create_withUnknownDesignationId_isRejected() {
        assertThatThrownBy(() -> skillService.create(new CreateSkillRequest("Ghost Skill " + unique(), UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withDuplicateNameUnderSameDesignation_isRejected() {
        DesignationResponse designation = createDesignation("Electrician");
        String name = "Wiring " + unique();
        skillService.create(new CreateSkillRequest(name, designation.id()));

        assertThatThrownBy(() -> skillService.create(new CreateSkillRequest(name, designation.id())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withSameNameUnderDifferentDesignations_isAllowed() {
        DesignationResponse designationA = createDesignation("Welder A");
        DesignationResponse designationB = createDesignation("Welder B");
        String name = "Arc Welding " + unique();

        skillService.create(new CreateSkillRequest(name, designationA.id()));
        SkillResponse second = skillService.create(new CreateSkillRequest(name, designationB.id()));

        assertThat(second.name()).isEqualTo(name);
    }

    @Test
    void update_canMoveSkillToADifferentDesignation() {
        DesignationResponse original = createDesignation("Original Designation");
        DesignationResponse target = createDesignation("Target Designation");
        SkillResponse created = skillService.create(new CreateSkillRequest("Relocatable Skill " + unique(), original.id()));

        SkillResponse updated = skillService.update(created.id(), new UpdateSkillRequest(created.name(), target.id()));

        assertThat(updated.designationId()).isEqualTo(target.id());
        assertThat(updated.designationName()).isEqualTo(target.name());
    }

    @Test
    void list_filteredByDesignation_onlyReturnsMatchingSkills() {
        DesignationResponse target = createDesignation("Crane Operator");
        DesignationResponse other = createDesignation("Unrelated Designation");
        SkillResponse inTarget = skillService.create(new CreateSkillRequest("Tower Crane " + unique(), target.id()));
        skillService.create(new CreateSkillRequest("Unrelated Skill " + unique(), other.id()));

        // Filtered strictly by this test's own freshly-created designation id - safe without ALSO
        // filtering by name/search, since no other test run could ever have created a skill under a
        // brand-new random designation id that didn't exist until this test created it.
        List<SkillResponse> filtered = skillService
                .list(new SkillSearchCriteria(Set.of(target.id()), null, null), PageRequest.of(0, 100))
                .content();

        assertThat(filtered).extracting(SkillResponse::id).contains(inTarget.id());
        assertThat(filtered).allSatisfy(s -> assertThat(s.designationId()).isEqualTo(target.id()));
    }

    @Test
    void list_filtersByMultipleDesignationIds_orWithinFieldAndAndAcrossFields() {
        DesignationResponse designationA = createDesignation("Multi A");
        DesignationResponse designationB = createDesignation("Multi B");
        DesignationResponse designationC = createDesignation("Multi C");
        String marker = "MultiFilterSkill " + unique();
        SkillResponse inA = skillService.create(new CreateSkillRequest(marker + " A", designationA.id()));
        SkillResponse inB = skillService.create(new CreateSkillRequest(marker + " B", designationB.id()));
        skillService.create(new CreateSkillRequest(marker + " C", designationC.id()));
        skillService.updateStatus(inB.id(), ActiveStatus.INACTIVE);

        // OR within designationIds: both A and B match, even though C (also matching the marker) is
        // excluded because its designation id isn't in the requested set.
        List<SkillResponse> byDesignations = skillService
                .list(new SkillSearchCriteria(Set.of(designationA.id(), designationB.id()), null, marker), PageRequest.of(0, 100))
                .content();
        assertThat(byDesignations).extracting(SkillResponse::id).containsExactlyInAnyOrder(inA.id(), inB.id());

        // AND across fields: designationIds (OR'd A/B) together with statuses=ACTIVE excludes inB,
        // which was just deactivated above, leaving only inA.
        List<SkillResponse> activeOnly = skillService
                .list(new SkillSearchCriteria(Set.of(designationA.id(), designationB.id()), Set.of(ActiveStatus.ACTIVE), marker),
                        PageRequest.of(0, 100))
                .content();
        assertThat(activeOnly).extracting(SkillResponse::id).containsExactly(inA.id());
    }

    @Test
    void list_includesResolvedDesignationNameForEachSkill() {
        DesignationResponse designation = createDesignation("Named Designation");
        String name = "Named Skill " + unique();
        SkillResponse created = skillService.create(new CreateSkillRequest(name, designation.id()));

        List<SkillResponse> page = skillService
                .list(new SkillSearchCriteria(null, null, name), PageRequest.of(0, 100))
                .content();

        assertThat(page).extracting(SkillResponse::id).containsExactly(created.id());
        assertThat(page.get(0).designationName()).isEqualTo(designation.name());
    }

    @Test
    void list_includesResolvedImageUrlForASkillThatHasOne() {
        DesignationResponse designation = createDesignation("Imaged Designation");
        String name = "Imaged Skill " + unique();
        SkillResponse created = skillService.create(new CreateSkillRequest(name, designation.id()));
        skillService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        List<SkillResponse> page = skillService
                .list(new SkillSearchCriteria(null, null, name), PageRequest.of(0, 100))
                .content();

        assertThat(page).extracting(SkillResponse::id).containsExactly(created.id());
        assertThat(page.get(0).imageUrl()).isNotBlank();
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        DesignationResponse designation = createDesignation("Security Guard");
        SkillResponse created = skillService.create(new CreateSkillRequest("CCTV Monitoring " + unique(), designation.id()));

        SkillResponse deactivated = skillService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        SkillResponse reactivated = skillService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void list_sortsByNameAscendingOrDescending() {
        DesignationResponse designation = createDesignation("SortSkillDesig");
        String prefix = "SortSkill" + unique();
        SkillResponse beta = skillService.create(new CreateSkillRequest(prefix + " Beta", designation.id()));
        SkillResponse alpha = skillService.create(new CreateSkillRequest(prefix + " Alpha", designation.id()));

        List<SkillResponse> ascending = skillService
                .list(new SkillSearchCriteria(null, null, prefix), PageRequest.of(0, 100, Sort.by("name").ascending()))
                .content();
        assertThat(ascending).extracting(SkillResponse::id).containsExactly(alpha.id(), beta.id());

        List<SkillResponse> descending = skillService
                .list(new SkillSearchCriteria(null, null, prefix), PageRequest.of(0, 100, Sort.by("name").descending()))
                .content();
        assertThat(descending).extracting(SkillResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> skillService.list(new SkillSearchCriteria(null, null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        DesignationResponse designation = createDesignation("Bulk Status Designation");
        SkillResponse first = skillService.create(new CreateSkillRequest("BulkSkill1 " + unique(), designation.id()));
        SkillResponse second = skillService.create(new CreateSkillRequest("BulkSkill2 " + unique(), designation.id()));

        BulkActionResult result = skillService.bulkUpdateStatus(Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(skillService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(skillService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        DesignationResponse designation = createDesignation("Bulk Status Partial");
        SkillResponse real = skillService.create(new CreateSkillRequest("BulkSkill3 " + unique(), designation.id()));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = skillService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(BulkActionFailure::id).containsExactly(bogusId);
        assertThat(skillService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        DesignationResponse designation = createDesignation("Driver");
        SkillResponse created = skillService.create(new CreateSkillRequest("Heavy Vehicle License " + unique(), designation.id()));

        SkillResponse withImage = skillService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
    }

    @Test
    void delete_removesASkillWithNoWorkersAssigned() {
        DesignationResponse designation = createDesignation("Deletable Designation");
        SkillResponse created = skillService.create(new CreateSkillRequest("Deletable Skill " + unique(), designation.id()));

        skillService.delete(created.id());

        assertThatThrownBy(() -> skillService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_aSkillStillAssignedToAWorker_isRejectedWithAClearMessage() {
        DesignationResponse designation = createDesignation("InUse Designation");
        SkillResponse created = skillService.create(new CreateSkillRequest("InUse Skill " + unique(), designation.id()));
        createWorkerWithSkill(designation.id(), created.id());

        assertThatThrownBy(() -> skillService.delete(created.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("workers");

        // Still there afterward - the failed delete must not have left it half-removed.
        assertThat(skillService.get(created.id())).isNotNull();
    }

    @Test
    void bulkDelete_partialSuccess_deletesTheFreeOneAndReportsTheBlockedOneWithoutLosingData() {
        DesignationResponse designation = createDesignation("Bulk Delete Designation");
        SkillResponse deletable = skillService.create(new CreateSkillRequest("BulkDelFree " + unique(), designation.id()));
        SkillResponse blocked = skillService.create(new CreateSkillRequest("BulkDelBlocked " + unique(), designation.id()));
        createWorkerWithSkill(designation.id(), blocked.id());

        BulkActionResult result = skillService.bulkDelete(Set.of(deletable.id(), blocked.id()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(blocked.id());

        assertThatThrownBy(() -> skillService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(skillService.get(blocked.id())).isNotNull();
    }
}
