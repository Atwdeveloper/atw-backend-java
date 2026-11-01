package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for skill-category CRUD against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
class SkillCategoryAdminIntegrationTest {

    @Autowired
    private SkillCategoryService skillCategoryService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_thenGet_roundTrips() {
        SkillCategoryDto created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Unskilled " + unique(), "UNS" + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        SkillCategoryDto fetched = skillCategoryService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
        assertThat(fetched.code()).isEqualTo(created.code());
    }

    @Test
    void create_withDuplicateName_isRejected() {
        String name = "Semi-skilled " + unique();
        skillCategoryService.create(new CreateSkillCategoryRequest(name, "SEM" + unique()));

        assertThatThrownBy(() -> skillCategoryService.create(new CreateSkillCategoryRequest(name, "SEM2" + unique())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withDuplicateCode_isRejected() {
        String code = "HSK" + unique();
        skillCategoryService.create(new CreateSkillCategoryRequest("Highly Skilled " + unique(), code));

        assertThatThrownBy(() -> skillCategoryService.create(new CreateSkillCategoryRequest("Highly Skilled Two " + unique(), code)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_renamesAndRecodes() {
        SkillCategoryDto created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Skilled " + unique(), "SK" + unique()));

        String newName = "Skilled Renamed " + unique();
        String newCode = "SKR" + unique();
        SkillCategoryDto updated = skillCategoryService.update(created.id(), new UpdateSkillCategoryRequest(newName, newCode));

        assertThat(updated.name()).isEqualTo(newName);
        assertThat(updated.code()).isEqualTo(newCode);
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        SkillCategoryDto created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Supervisory " + unique(), "SUP" + unique()));

        SkillCategoryDto deactivated = skillCategoryService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        SkillCategoryDto reactivated = skillCategoryService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void list_includesCreatedSkillCategories() {
        SkillCategoryDto created = skillCategoryService.create(
                new CreateSkillCategoryRequest("Clerical " + unique(), "CLR" + unique()));

        assertThat(skillCategoryService.list()).extracting(SkillCategoryDto::id).contains(created.id());
    }
}
