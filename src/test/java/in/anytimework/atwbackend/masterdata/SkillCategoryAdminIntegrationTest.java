package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillCategorySearchCriteria;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for skill-category CRUD against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
class SkillCategoryAdminIntegrationTest {

    @Autowired
    private SkillCategoryService skillCategoryService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A designation assigned to {@code skillCategoryId} - exists purely to exercise the delete-guard tests below. */
    private void createDesignationInSkillCategory(UUID skillCategoryId) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        designationService.create(new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), skillCategoryId));
    }

    @Test
    void create_thenGet_roundTrips() {
        SkillCategoryResponse created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Unskilled " + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        SkillCategoryResponse fetched = skillCategoryService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
        assertThat(fetched.code()).isEqualTo(created.code());
    }

    @Test
    void create_generatesAUniqueCodeServerSide() {
        SkillCategoryResponse first = skillCategoryService.create(new CreateSkillCategoryRequest("CodeGenA " + unique()));
        SkillCategoryResponse second = skillCategoryService.create(new CreateSkillCategoryRequest("CodeGenB " + unique()));

        assertThat(first.code()).startsWith("SKC");
        assertThat(second.code()).startsWith("SKC");
        assertThat(first.code()).isNotEqualTo(second.code());
    }

    @Test
    void create_withDuplicateName_isRejected() {
        String name = "Semi-skilled " + unique();
        skillCategoryService.create(new CreateSkillCategoryRequest(name));

        assertThatThrownBy(() -> skillCategoryService.create(new CreateSkillCategoryRequest(name)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_renames() {
        SkillCategoryResponse created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Skilled " + unique()));

        String newName = "Skilled Renamed " + unique();
        SkillCategoryResponse updated = skillCategoryService.update(created.id(), new UpdateSkillCategoryRequest(newName));

        assertThat(updated.name()).isEqualTo(newName);
        // code is assigned once at creation and never changes, even across an unrelated rename.
        assertThat(updated.code()).isEqualTo(created.code());
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        SkillCategoryResponse created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Supervisory " + unique()));

        SkillCategoryResponse deactivated = skillCategoryService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        SkillCategoryResponse reactivated = skillCategoryService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void list_includesCreatedSkillCategories() {
        String name = "Clerical " + unique();
        SkillCategoryResponse created = skillCategoryService.create(new CreateSkillCategoryRequest(name));

        // Filtered by this test's own unique name, not an unfiltered first page - this test database
        // accumulates rows across every test run in a session, so an unfiltered page 0 can't be
        // assumed to contain a just-created row once enough prior runs have piled up ahead of it.
        List<SkillCategoryResponse> page = skillCategoryService
                .list(new SkillCategorySearchCriteria(null, name), PageRequest.of(0, 100))
                .content();
        assertThat(page).extracting(SkillCategoryResponse::id).containsExactly(created.id());
    }

    @Test
    void list_filtersByMultipleStatusesAndSearch() {
        // Shared unique prefix lets one search term find both rows below (an AND against the real,
        // ever-growing table), while the two-value statuses set proves OR-within-the-field.
        String prefix = "MultiFilter " + unique();
        SkillCategoryResponse active = skillCategoryService.create(new CreateSkillCategoryRequest(prefix + " Active"));
        SkillCategoryResponse inactive = skillCategoryService.create(new CreateSkillCategoryRequest(prefix + " Inactive"));
        skillCategoryService.updateStatus(inactive.id(), ActiveStatus.INACTIVE);

        List<SkillCategoryResponse> bothStatuses = skillCategoryService
                .list(new SkillCategorySearchCriteria(Set.of(ActiveStatus.ACTIVE, ActiveStatus.INACTIVE), prefix),
                        PageRequest.of(0, 100))
                .content();
        assertThat(bothStatuses).extracting(SkillCategoryResponse::id)
                .containsExactlyInAnyOrder(active.id(), inactive.id());

        List<SkillCategoryResponse> activeOnly = skillCategoryService
                .list(new SkillCategorySearchCriteria(Set.of(ActiveStatus.ACTIVE), prefix), PageRequest.of(0, 100))
                .content();
        assertThat(activeOnly).extracting(SkillCategoryResponse::id).containsExactly(active.id());
    }

    @Test
    void list_sortsByNameAscendingOrDescending() {
        String prefix = "SortSkillCat" + unique();
        SkillCategoryResponse beta = skillCategoryService.create(new CreateSkillCategoryRequest(prefix + " Beta"));
        SkillCategoryResponse alpha = skillCategoryService.create(new CreateSkillCategoryRequest(prefix + " Alpha"));

        List<SkillCategoryResponse> ascending = skillCategoryService
                .list(new SkillCategorySearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").ascending()))
                .content();
        assertThat(ascending).extracting(SkillCategoryResponse::id).containsExactly(alpha.id(), beta.id());

        List<SkillCategoryResponse> descending = skillCategoryService
                .list(new SkillCategorySearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").descending()))
                .content();
        assertThat(descending).extracting(SkillCategoryResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> skillCategoryService.list(new SkillCategorySearchCriteria(null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        SkillCategoryResponse first = skillCategoryService.create(new CreateSkillCategoryRequest("Bulk1 " + unique()));
        SkillCategoryResponse second = skillCategoryService.create(new CreateSkillCategoryRequest("Bulk2 " + unique()));

        BulkActionResult result = skillCategoryService.bulkUpdateStatus(
                Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(skillCategoryService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(skillCategoryService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        SkillCategoryResponse real = skillCategoryService.create(new CreateSkillCategoryRequest("Bulk3 " + unique()));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = skillCategoryService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.id()).containsExactly(bogusId);
        assertThat(skillCategoryService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void delete_removesASkillCategoryWithNoReferences() {
        SkillCategoryResponse created = skillCategoryService.create(new CreateSkillCategoryRequest("Deletable " + unique()));

        skillCategoryService.delete(created.id());

        assertThatThrownBy(() -> skillCategoryService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_aSkillCategoryStillAssignedToADesignation_isRejectedWithAClearMessage() {
        SkillCategoryResponse created = skillCategoryService.create(new CreateSkillCategoryRequest("InUse " + unique()));
        createDesignationInSkillCategory(created.id());

        assertThatThrownBy(() -> skillCategoryService.delete(created.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("designations");

        // Still there afterward - the failed delete must not have left it half-removed.
        assertThat(skillCategoryService.get(created.id())).isNotNull();
    }

    @Test
    void bulkDelete_partialSuccess_deletesTheFreeOneAndReportsTheBlockedOneWithoutLosingData() {
        SkillCategoryResponse deletable = skillCategoryService.create(new CreateSkillCategoryRequest("BulkDel1 " + unique()));
        SkillCategoryResponse blocked = skillCategoryService.create(new CreateSkillCategoryRequest("BulkDel2 " + unique()));
        createDesignationInSkillCategory(blocked.id());

        BulkActionResult result = skillCategoryService.bulkDelete(Set.of(deletable.id(), blocked.id()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(blocked.id());

        assertThatThrownBy(() -> skillCategoryService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(skillCategoryService.get(blocked.id())).isNotNull();
    }
}
