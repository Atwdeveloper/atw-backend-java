package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDesignationRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustrySummary;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private IndustryDto createIndustry(String label) {
        return industryService.create(new CreateIndustryRequest(label + " " + unique(), label.substring(0, Math.min(3, label.length())).toUpperCase() + unique()));
    }

    @Test
    void create_thenGet_roundTripsWithIndustries() {
        IndustryDto construction = createIndustry("Construction");

        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Site Supervisor " + unique(), Set.of(construction.id()), null));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.industries()).extracting(IndustrySummary::id).containsExactly(construction.id());
        DesignationDto fetched = designationService.get(created.id());
        assertThat(fetched.industries()).extracting(IndustrySummary::id).containsExactly(construction.id());
    }

    @Test
    void create_canBelongToMultipleIndustries() {
        IndustryDto a = createIndustry("Warehousing");
        IndustryDto b = createIndustry("Logistics");

        DesignationDto created = designationService.create(
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
        IndustryDto industry = createIndustry("Retail");
        String name = "Cashier " + unique();
        designationService.create(new CreateDesignationRequest(name, Set.of(industry.id()), null));

        assertThatThrownBy(() -> designationService.create(new CreateDesignationRequest(name, Set.of(industry.id()), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_replacesIndustryAssignment() {
        IndustryDto first = createIndustry("Hospitality");
        IndustryDto second = createIndustry("Events");
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Front Desk " + unique(), Set.of(first.id()), null));

        DesignationDto updated = designationService.update(created.id(),
                new UpdateDesignationRequest(created.name(), Set.of(second.id())));

        assertThat(updated.industries()).extracting(IndustrySummary::id).containsExactly(second.id());
    }

    @Test
    void list_filteredByIndustry_onlyReturnsMatchingDesignations() {
        IndustryDto target = createIndustry("Manufacturing");
        IndustryDto other = createIndustry("Unrelated");
        DesignationDto inTarget = designationService.create(
                new CreateDesignationRequest("Machine Operator " + unique(), Set.of(target.id()), null));
        designationService.create(new CreateDesignationRequest("Unrelated Role " + unique(), Set.of(other.id()), null));

        var filtered = designationService.list(target.id());

        assertThat(filtered).extracting(DesignationDto::id).contains(inTarget.id());
        assertThat(filtered).allSatisfy(d ->
                assertThat(d.industries()).extracting(IndustrySummary::id).contains(target.id()));
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        IndustryDto industry = createIndustry("Mining");
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Driller " + unique(), Set.of(industry.id()), null));

        DesignationDto deactivated = designationService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        DesignationDto reactivated = designationService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        IndustryDto industry = createIndustry("Packaging");
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Packer " + unique(), Set.of(industry.id()), null));

        DesignationDto withImage = designationService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
    }

    @Test
    void create_withSkillCategory_assignsIt() {
        IndustryDto industry = createIndustry("Textiles");
        SkillCategoryDto category = skillCategoryService.create(new CreateSkillCategoryRequest("Unskilled " + unique(), "UNS" + unique()));

        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Loom Operator " + unique(), Set.of(industry.id()), category.id()));

        assertThat(created.skillCategoryId()).isEqualTo(category.id());
        assertThat(designationService.get(created.id()).skillCategoryId()).isEqualTo(category.id());
    }

    @Test
    void update_doesNotTouchAPreviouslyAssignedSkillCategory() {
        // Regression: update() must never silently clear skillCategoryId just because the general
        // update request doesn't mention it - that's what the dedicated assign/clear endpoints are for.
        IndustryDto industry = createIndustry("Leather");
        SkillCategoryDto category = skillCategoryService.create(new CreateSkillCategoryRequest("Skilled " + unique(), "SKL" + unique()));
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Tanner " + unique(), Set.of(industry.id()), category.id()));

        DesignationDto updated = designationService.update(created.id(),
                new UpdateDesignationRequest("Tanner Renamed " + unique(), Set.of(industry.id())));

        assertThat(updated.skillCategoryId()).isEqualTo(category.id());
    }

    @Test
    void assignSkillCategory_thenClear_roundTrips() {
        IndustryDto industry = createIndustry("Furniture");
        SkillCategoryDto category = skillCategoryService.create(new CreateSkillCategoryRequest("Carpentry " + unique(), "CAR" + unique()));
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Cabinet Maker " + unique(), Set.of(industry.id()), null));
        assertThat(created.skillCategoryId()).isNull();

        DesignationDto assigned = designationService.assignSkillCategory(created.id(), category.id());
        assertThat(assigned.skillCategoryId()).isEqualTo(category.id());

        DesignationDto cleared = designationService.clearSkillCategory(created.id());
        assertThat(cleared.skillCategoryId()).isNull();
    }

    @Test
    void assignSkillCategory_withUnknownId_isRejected() {
        IndustryDto industry = createIndustry("Glassware");
        DesignationDto created = designationService.create(
                new CreateDesignationRequest("Glass Blower " + unique(), Set.of(industry.id()), null));

        assertThatThrownBy(() -> designationService.assignSkillCategory(created.id(), UUID.randomUUID()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withUnknownSkillCategory_isRejected() {
        IndustryDto industry = createIndustry("Printing");

        assertThatThrownBy(() -> designationService.create(
                new CreateDesignationRequest("Press Operator " + unique(), Set.of(industry.id()), UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
    }
}
