package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.masterdata.application.SkillService;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
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

/** End-to-end coverage for designation CRUD + industry assignment + icon upload against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class DesignationAdminIntegrationTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    @Autowired
    private DesignationService designationService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private SkillCategoryService skillCategoryService;
    @Autowired
    private SkillService skillService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private IndustryResponse createIndustry(String label) {
        return industryService.create(new CreateIndustryRequest(label + " " + unique()));
    }

    @Test
    void create_thenGet_roundTripsWithIndustries() {
        IndustryResponse construction = createIndustry("Construction");

        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Site Supervisor " + unique(), Set.of(construction.id()), null));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.industries()).extracting(IndustrySummary::id).containsExactly(construction.id());
        DesignationResponse fetched = designationService.get(created.id());
        assertThat(fetched.industries()).extracting(IndustrySummary::id).containsExactly(construction.id());
    }

    @Test
    void create_canBelongToMultipleIndustries() {
        IndustryResponse a = createIndustry("Warehousing");
        IndustryResponse b = createIndustry("Logistics");

        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Forklift Operator " + unique(), Set.of(a.id(), b.id()), null));

        assertThat(created.industries()).extracting(IndustrySummary::id).containsExactlyInAnyOrder(a.id(), b.id());
    }

    @Test
    void create_withNoIndustries_isRejected() {
        assertThatThrownBy(() -> designationService.create(new CreateDesignationRequest("Orphan Role " + unique(), Set.of(), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withUnknownIndustryId_isRejected() {
        assertThatThrownBy(() -> designationService.create(
                new CreateDesignationRequest("Ghost Role " + unique(), Set.of(UUID.randomUUID()), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withDuplicateName_isRejected() {
        IndustryResponse industry = createIndustry("Retail");
        String name = "Cashier " + unique();
        designationService.create(new CreateDesignationRequest(name, Set.of(industry.id()), null));

        assertThatThrownBy(() -> designationService.create(new CreateDesignationRequest(name, Set.of(industry.id()), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_replacesIndustryAssignment() {
        IndustryResponse first = createIndustry("Hospitality");
        IndustryResponse second = createIndustry("Events");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Front Desk " + unique(), Set.of(first.id()), null));

        DesignationResponse updated = designationService.update(created.id(),
                new UpdateDesignationRequest(created.name(), Set.of(second.id())));

        assertThat(updated.industries()).extracting(IndustrySummary::id).containsExactly(second.id());
    }

    @Test
    void list_filteredByIndustry_onlyReturnsMatchingDesignations() {
        IndustryResponse target = createIndustry("Manufacturing");
        IndustryResponse other = createIndustry("Unrelated");
        String name = "Machine Operator " + unique();
        DesignationResponse inTarget = designationService.create(
                new CreateDesignationRequest(name, Set.of(target.id()), null));
        designationService.create(new CreateDesignationRequest("Unrelated Role " + unique(), Set.of(other.id()), null));

        // Filtered by this test's own unique name too (gotcha: an unfiltered/industry-only page can't
        // be assumed free of unrelated rows from other tests/runs that also reference this industry).
        var filtered = designationService
                .list(new DesignationSearchCriteria(Set.of(target.id()), null, null, name), PageRequest.of(0, 100))
                .content();

        assertThat(filtered).extracting(DesignationResponse::id).containsExactly(inTarget.id());
        assertThat(filtered).allSatisfy(d ->
                assertThat(d.industries()).extracting(IndustrySummary::id).contains(target.id()));
    }

    @Test
    void list_multiselectIndustryFilter_orsWithinFieldAndAndsAcrossFields() {
        String token = "Multi" + unique();
        IndustryResponse indA = createIndustry("MultiIndA");
        IndustryResponse indB = createIndustry("MultiIndB");
        DesignationResponse inA = designationService.create(
                new CreateDesignationRequest(token + " InA", Set.of(indA.id()), null));
        DesignationResponse inB = designationService.create(
                new CreateDesignationRequest(token + " InB", Set.of(indB.id()), null));
        designationService.updateStatus(inB.id(), ActiveStatus.INACTIVE);

        // OR within the industryIds field: a designation matching EITHER selected industry comes back,
        // regardless of status.
        List<DesignationResponse> byEitherIndustry = designationService
                .list(new DesignationSearchCriteria(Set.of(indA.id(), indB.id()), null, null, token), PageRequest.of(0, 100))
                .content();
        assertThat(byEitherIndustry).extracting(DesignationResponse::id).containsExactlyInAnyOrder(inA.id(), inB.id());

        // AND across fields: adding statuses=ACTIVE narrows this down to only the active one, even
        // though both designations still individually satisfy the industryIds filter on its own.
        List<DesignationResponse> activeOnly = designationService
                .list(new DesignationSearchCriteria(Set.of(indA.id(), indB.id()), null, Set.of(ActiveStatus.ACTIVE), token),
                        PageRequest.of(0, 100))
                .content();
        assertThat(activeOnly).extracting(DesignationResponse::id).containsExactly(inA.id());
    }

    @Test
    void list_resolvesImageUrlForARowThatHasOne() {
        String name = "ImageInList " + unique();
        IndustryResponse industry = createIndustry("ImageListIndustry");
        DesignationResponse created = designationService.create(new CreateDesignationRequest(name, Set.of(industry.id()), null));
        designationService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        List<DesignationResponse> page = designationService
                .list(new DesignationSearchCriteria(null, null, null, name), PageRequest.of(0, 100))
                .content();

        assertThat(page).hasSize(1);
        assertThat(page.get(0).imageUrl()).isNotBlank();
    }

    @Test
    void list_sortsByNameAscendingOrDescending() {
        IndustryResponse industry = createIndustry("SortDesigIndustry");
        String prefix = "SortDesig" + unique();
        DesignationResponse beta = designationService.create(new CreateDesignationRequest(prefix + " Beta", Set.of(industry.id()), null));
        DesignationResponse alpha = designationService.create(new CreateDesignationRequest(prefix + " Alpha", Set.of(industry.id()), null));

        List<DesignationResponse> ascending = designationService
                .list(new DesignationSearchCriteria(null, null, null, prefix), PageRequest.of(0, 100, Sort.by("name").ascending()))
                .content();
        assertThat(ascending).extracting(DesignationResponse::id).containsExactly(alpha.id(), beta.id());

        List<DesignationResponse> descending = designationService
                .list(new DesignationSearchCriteria(null, null, null, prefix), PageRequest.of(0, 100, Sort.by("name").descending()))
                .content();
        assertThat(descending).extracting(DesignationResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> designationService.list(new DesignationSearchCriteria(null, null, null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        IndustryResponse industry = createIndustry("BulkStatusIndustry");
        DesignationResponse first = designationService.create(
                new CreateDesignationRequest("BulkStatus1 " + unique(), Set.of(industry.id()), null));
        DesignationResponse second = designationService.create(
                new CreateDesignationRequest("BulkStatus2 " + unique(), Set.of(industry.id()), null));

        BulkActionResult result = designationService.bulkUpdateStatus(
                Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(designationService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(designationService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        IndustryResponse industry = createIndustry("BulkStatusIndustry2");
        DesignationResponse real = designationService.create(
                new CreateDesignationRequest("BulkStatus3 " + unique(), Set.of(industry.id()), null));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = designationService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.id()).containsExactly(bogusId);
        assertThat(designationService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void delete_removesADesignationWithNoReferences() {
        IndustryResponse industry = createIndustry("DeletableIndustry");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Deletable " + unique(), Set.of(industry.id()), null));

        designationService.delete(created.id());

        assertThatThrownBy(() -> designationService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_aDesignationStillReferencedByASkill_isRejectedWithAClearMessage() {
        IndustryResponse industry = createIndustry("InUseIndustry");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("InUse " + unique(), Set.of(industry.id()), null));
        skillService.create(new CreateSkillRequest("Blocking Skill " + unique(), created.id()));

        assertThatThrownBy(() -> designationService.delete(created.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("skills or workers");

        // Still there afterward - the failed delete must not have left it half-removed.
        assertThat(designationService.get(created.id())).isNotNull();
    }

    @Test
    void bulkDelete_partialSuccess_deletesTheFreeOneAndReportsTheBlockedOneWithoutLosingData() {
        IndustryResponse industry = createIndustry("BulkDelIndustry");
        DesignationResponse deletable = designationService.create(
                new CreateDesignationRequest("BulkDel1 " + unique(), Set.of(industry.id()), null));
        DesignationResponse blocked = designationService.create(
                new CreateDesignationRequest("BulkDel2 " + unique(), Set.of(industry.id()), null));
        skillService.create(new CreateSkillRequest("Blocking Skill 2 " + unique(), blocked.id()));

        BulkActionResult result = designationService.bulkDelete(Set.of(deletable.id(), blocked.id()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(blocked.id());

        assertThatThrownBy(() -> designationService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(designationService.get(blocked.id())).isNotNull();
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        IndustryResponse industry = createIndustry("Mining");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Driller " + unique(), Set.of(industry.id()), null));

        DesignationResponse deactivated = designationService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        DesignationResponse reactivated = designationService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        IndustryResponse industry = createIndustry("Packaging");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Packer " + unique(), Set.of(industry.id()), null));

        DesignationResponse withImage = designationService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
    }

    @Test
    void create_withSkillCategory_assignsIt() {
        IndustryResponse industry = createIndustry("Textiles");
        SkillCategoryResponse category = skillCategoryService.create(new CreateSkillCategoryRequest("Unskilled " + unique()));

        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Loom Operator " + unique(), Set.of(industry.id()), category.id()));

        assertThat(created.skillCategoryId()).isEqualTo(category.id());
        assertThat(designationService.get(created.id()).skillCategoryId()).isEqualTo(category.id());
    }

    @Test
    void update_doesNotTouchAPreviouslyAssignedSkillCategory() {
        // Regression: update() must never silently clear skillCategoryId just because the general
        // update request doesn't mention it - that's what the dedicated assign/clear endpoints are for.
        IndustryResponse industry = createIndustry("Leather");
        SkillCategoryResponse category = skillCategoryService.create(new CreateSkillCategoryRequest("Skilled " + unique()));
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Tanner " + unique(), Set.of(industry.id()), category.id()));

        DesignationResponse updated = designationService.update(created.id(),
                new UpdateDesignationRequest("Tanner Renamed " + unique(), Set.of(industry.id())));

        assertThat(updated.skillCategoryId()).isEqualTo(category.id());
    }

    @Test
    void assignSkillCategory_thenClear_roundTrips() {
        IndustryResponse industry = createIndustry("Furniture");
        SkillCategoryResponse category = skillCategoryService.create(new CreateSkillCategoryRequest("Carpentry " + unique()));
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Cabinet Maker " + unique(), Set.of(industry.id()), null));
        assertThat(created.skillCategoryId()).isNull();

        DesignationResponse assigned = designationService.assignSkillCategory(created.id(), category.id());
        assertThat(assigned.skillCategoryId()).isEqualTo(category.id());

        DesignationResponse cleared = designationService.clearSkillCategory(created.id());
        assertThat(cleared.skillCategoryId()).isNull();
    }

    @Test
    void assignSkillCategory_withUnknownId_isRejected() {
        IndustryResponse industry = createIndustry("Glassware");
        DesignationResponse created = designationService.create(
                new CreateDesignationRequest("Glass Blower " + unique(), Set.of(industry.id()), null));

        assertThatThrownBy(() -> designationService.assignSkillCategory(created.id(), UUID.randomUUID()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withUnknownSkillCategory_isRejected() {
        IndustryResponse industry = createIndustry("Printing");

        assertThatThrownBy(() -> designationService.create(
                new CreateDesignationRequest("Press Operator " + unique(), Set.of(industry.id()), UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
    }
}
