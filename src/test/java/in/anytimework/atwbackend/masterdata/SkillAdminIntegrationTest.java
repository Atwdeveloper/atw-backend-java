package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.SkillService;
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

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private DesignationDto createDesignation(String label) {
        IndustryDto industry = industryService.create(new CreateIndustryRequest(label + " Industry " + unique(), "IND" + unique()));
        return designationService.create(new CreateDesignationRequest(label + " " + unique(), Set.of(industry.id()), null));
    }

    @Test
    void create_thenGet_roundTrips() {
        DesignationDto designation = createDesignation("Warehouse Helper");

        SkillDto created = skillService.create(new CreateSkillRequest("Forklift Operation " + unique(), designation.id()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.designationId()).isEqualTo(designation.id());
        SkillDto fetched = skillService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
    }

    @Test
    void create_withUnknownDesignationId_isRejected() {
        assertThatThrownBy(() -> skillService.create(new CreateSkillRequest("Ghost Skill " + unique(), UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withDuplicateNameUnderSameDesignation_isRejected() {
        DesignationDto designation = createDesignation("Electrician");
        String name = "Wiring " + unique();
        skillService.create(new CreateSkillRequest(name, designation.id()));

        assertThatThrownBy(() -> skillService.create(new CreateSkillRequest(name, designation.id())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withSameNameUnderDifferentDesignations_isAllowed() {
        DesignationDto designationA = createDesignation("Welder A");
        DesignationDto designationB = createDesignation("Welder B");
        String name = "Arc Welding " + unique();

        skillService.create(new CreateSkillRequest(name, designationA.id()));
        SkillDto second = skillService.create(new CreateSkillRequest(name, designationB.id()));

        assertThat(second.name()).isEqualTo(name);
    }

    @Test
    void update_canMoveSkillToADifferentDesignation() {
        DesignationDto original = createDesignation("Original Designation");
        DesignationDto target = createDesignation("Target Designation");
        SkillDto created = skillService.create(new CreateSkillRequest("Relocatable Skill " + unique(), original.id()));

        SkillDto updated = skillService.update(created.id(), new UpdateSkillRequest(created.name(), target.id()));

        assertThat(updated.designationId()).isEqualTo(target.id());
    }

    @Test
    void list_filteredByDesignation_onlyReturnsMatchingSkills() {
        DesignationDto target = createDesignation("Crane Operator");
        DesignationDto other = createDesignation("Unrelated Designation");
        SkillDto inTarget = skillService.create(new CreateSkillRequest("Tower Crane " + unique(), target.id()));
        skillService.create(new CreateSkillRequest("Unrelated Skill " + unique(), other.id()));

        var filtered = skillService.list(target.id());

        assertThat(filtered).extracting(SkillDto::id).contains(inTarget.id());
        assertThat(filtered).allSatisfy(s -> assertThat(s.designationId()).isEqualTo(target.id()));
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        DesignationDto designation = createDesignation("Security Guard");
        SkillDto created = skillService.create(new CreateSkillRequest("CCTV Monitoring " + unique(), designation.id()));

        SkillDto deactivated = skillService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        SkillDto reactivated = skillService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        DesignationDto designation = createDesignation("Driver");
        SkillDto created = skillService.create(new CreateSkillRequest("Heavy Vehicle License " + unique(), designation.id()));

        SkillDto withImage = skillService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
    }
}
