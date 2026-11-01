package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryDto;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateService;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for minimum-wage-rate create/resolve/history against the real local Postgres -
 * in particular, that effective-dated resolution actually picks the right version as of a date, and
 * that deactivating a version removes it from resolution.
 */
@SpringBootTest
@ActiveProfiles("test")
class MinimumWageRateAdminIntegrationTest {

    @Autowired
    private MinimumWageRateService minimumWageRateService;
    @Autowired
    private SkillCategoryService skillCategoryService;
    @Autowired
    private StateRepository states;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID rajasthanId() {
        return states.findAll().stream().filter(s -> s.getCode().equals("RJ")).findFirst()
                .map(State::getId).orElseThrow();
    }

    private UUID createSkillCategory(String label) {
        SkillCategoryDto category = skillCategoryService.create(new CreateSkillCategoryRequest(label + " " + unique(), "SC" + unique()));
        return category.id();
    }

    @Test
    void create_thenGet_roundTrips_withResolvedNames() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Unskilled");

        MinimumWageRateDto created = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("450.00"), LocalDate.of(2026, 1, 1)));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.stateName()).isNotBlank();
        assertThat(created.skillCategoryName()).isNotBlank();
        MinimumWageRateDto fetched = minimumWageRateService.get(created.id());
        assertThat(fetched.minDailyRate()).isEqualByComparingTo("450.00");
    }

    @Test
    void create_withSameStateCategoryAndEffectiveDate_isRejected() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Skilled");
        LocalDate effectiveFrom = LocalDate.of(2026, 2, 1);
        minimumWageRateService.create(new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("500.00"), effectiveFrom));

        assertThatThrownBy(() -> minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("510.00"), effectiveFrom)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withSameStateCategoryButDifferentEffectiveDate_isANewVersion() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Semi-skilled");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));

        MinimumWageRateDto secondVersion = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("420.00"), LocalDate.of(2026, 4, 1)));

        assertThat(secondVersion.minDailyRate()).isEqualByComparingTo("420.00");
    }

    @Test
    void create_withUnknownState_isRejected() {
        UUID categoryId = createSkillCategory("Driver");

        assertThatThrownBy(() -> minimumWageRateService.create(
                new CreateMinimumWageRateRequest(UUID.randomUUID(), categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_withUnknownSkillCategory_isRejected() {
        UUID stateId = rajasthanId();

        assertThatThrownBy(() -> minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, UUID.randomUUID(), new BigDecimal("400.00"), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveEffective_picksTheLatestVersionAtOrBeforeTheGivenDate() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Mason");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("460.00"), LocalDate.of(2026, 6, 1)));

        assertThat(minimumWageRateService.resolveEffective(stateId, categoryId, LocalDate.of(2026, 3, 1)).minDailyRate())
                .isEqualByComparingTo("400.00");
        assertThat(minimumWageRateService.resolveEffective(stateId, categoryId, LocalDate.of(2026, 6, 1)).minDailyRate())
                .isEqualByComparingTo("460.00");
        assertThat(minimumWageRateService.resolveEffective(stateId, categoryId, LocalDate.of(2026, 12, 31)).minDailyRate())
                .isEqualByComparingTo("460.00");
    }

    @Test
    void resolveEffective_beforeAnyVersionExists_throwsNotFound() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Welder");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 6, 1)));

        assertThatThrownBy(() -> minimumWageRateService.resolveEffective(stateId, categoryId, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatingTheCurrentVersion_fallsBackToTheNextActiveOne() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Electrician");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        MinimumWageRateDto latest = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("460.00"), LocalDate.of(2026, 6, 1)));

        minimumWageRateService.updateStatus(latest.id(), ActiveStatus.INACTIVE);

        assertThat(minimumWageRateService.resolveEffective(stateId, categoryId, LocalDate.of(2026, 12, 31)).minDailyRate())
                .isEqualByComparingTo("400.00");
    }

    @Test
    void list_filteredByStateAndSkillCategory_returnsOnlyThatPairsHistory() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Plumber");
        UUID otherCategoryId = createSkillCategory("Carpenter");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("420.00"), LocalDate.of(2026, 6, 1)));
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, otherCategoryId, new BigDecimal("500.00"), LocalDate.of(2026, 1, 1)));

        var history = minimumWageRateService.list(stateId, categoryId, Pageable.unpaged());

        assertThat(history.content()).hasSize(2);
        assertThat(history.content()).allSatisfy(r -> assertThat(r.skillCategoryId()).isEqualTo(categoryId));
    }

    @Test
    void list_withNoFilters_isPaginated() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Mechanic");
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("420.00"), LocalDate.of(2026, 6, 1)));

        var firstPage = minimumWageRateService.list(null, null, org.springframework.data.domain.PageRequest.of(0, 1));

        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isGreaterThanOrEqualTo(2);
    }
}
