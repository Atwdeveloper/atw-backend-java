package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateSkillCategoryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.SkillCategoryResponse;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateService;
import in.anytimework.atwbackend.masterdata.application.SkillCategoryService;
import in.anytimework.atwbackend.masterdata.domain.State;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
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

    private UUID stateIdByCode(String code) {
        return states.findAll().stream().filter(s -> s.getCode().equals(code)).findFirst()
                .map(State::getId).orElseThrow();
    }

    private UUID rajasthanId() {
        return stateIdByCode("RJ");
    }

    private UUID createSkillCategory(String label) {
        SkillCategoryResponse category = skillCategoryService.create(new CreateSkillCategoryRequest(label + " " + unique()));
        return category.id();
    }

    @Test
    void create_thenGet_roundTrips_withResolvedNames() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("Unskilled");

        MinimumWageRateResponse created = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("450.00"), LocalDate.of(2026, 1, 1)));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.stateName()).isNotBlank();
        assertThat(created.skillCategoryName()).isNotBlank();
        MinimumWageRateResponse fetched = minimumWageRateService.get(created.id());
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

        MinimumWageRateResponse secondVersion = minimumWageRateService.create(
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
        MinimumWageRateResponse latest = minimumWageRateService.create(
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

        var history = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(Set.of(stateId), Set.of(categoryId), null), Pageable.unpaged());

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

        var firstPage = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(null, null, null), PageRequest.of(0, 1));

        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void list_filtersWithMultiselect_orWithinAFieldAndAndAcrossFields() {
        UUID rajasthan = rajasthanId();
        UUID maharashtra = stateIdByCode("MH");
        UUID categoryX = createSkillCategory("MultiFilterX");
        UUID categoryY = createSkillCategory("MultiFilterY");
        MinimumWageRateResponse rajasthanCategoryX = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(rajasthan, categoryX, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        MinimumWageRateResponse rajasthanCategoryY = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(rajasthan, categoryY, new BigDecimal("410.00"), LocalDate.of(2026, 1, 1)));
        // Same two skill categories, but a different state - must be excluded by the stateIds filter
        // below even though it matches the skillCategoryIds filter, proving AND-across-fields.
        minimumWageRateService.create(
                new CreateMinimumWageRateRequest(maharashtra, categoryX, new BigDecimal("420.00"), LocalDate.of(2026, 1, 1)));

        var page = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(Set.of(rajasthan), Set.of(categoryX, categoryY), null),
                PageRequest.of(0, 100));

        // Both categoryX and categoryY rows for rajasthan come back (OR within skillCategoryIds),
        // while the maharashtra/categoryX row is excluded (AND with stateIds).
        assertThat(page.content()).extracting(MinimumWageRateResponse::id)
                .containsExactlyInAnyOrder(rajasthanCategoryX.id(), rajasthanCategoryY.id());
    }

    @Test
    void list_filtersByStatus() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("StatusFilterTarget");
        MinimumWageRateResponse created = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        minimumWageRateService.updateStatus(created.id(), ActiveStatus.INACTIVE);

        var activeOnly = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(null, Set.of(categoryId), Set.of(ActiveStatus.ACTIVE)),
                PageRequest.of(0, 100)).content();
        assertThat(activeOnly).extracting(MinimumWageRateResponse::id).doesNotContain(created.id());

        var inactiveOnly = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(null, Set.of(categoryId), Set.of(ActiveStatus.INACTIVE)),
                PageRequest.of(0, 100)).content();
        assertThat(inactiveOnly).extracting(MinimumWageRateResponse::id).containsExactly(created.id());
    }

    @Test
    void list_sortsByEffectiveFromAscendingOrDescending() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("SortRate");
        MinimumWageRateResponse earlier = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        MinimumWageRateResponse later = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("420.00"), LocalDate.of(2026, 6, 1)));

        var ascending = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(null, Set.of(categoryId), null),
                PageRequest.of(0, 100, Sort.by("effectiveFrom").ascending())).content();
        assertThat(ascending).extracting(MinimumWageRateResponse::id).containsExactly(earlier.id(), later.id());

        var descending = minimumWageRateService.list(
                new MinimumWageRateSearchCriteria(null, Set.of(categoryId), null),
                PageRequest.of(0, 100, Sort.by("effectiveFrom").descending())).content();
        assertThat(descending).extracting(MinimumWageRateResponse::id).containsExactly(later.id(), earlier.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> minimumWageRateService.list(new MinimumWageRateSearchCriteria(null, null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        UUID stateId = rajasthanId();
        UUID categoryIdA = createSkillCategory("BulkStatusA");
        UUID categoryIdB = createSkillCategory("BulkStatusB");
        MinimumWageRateResponse first = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryIdA, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        MinimumWageRateResponse second = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryIdB, new BigDecimal("410.00"), LocalDate.of(2026, 1, 1)));

        BulkActionResult result = minimumWageRateService.bulkUpdateStatus(
                Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(minimumWageRateService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(minimumWageRateService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        UUID stateId = rajasthanId();
        UUID categoryId = createSkillCategory("BulkStatusC");
        MinimumWageRateResponse real = minimumWageRateService.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), LocalDate.of(2026, 1, 1)));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = minimumWageRateService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.id()).containsExactly(bogusId);
        assertThat(minimumWageRateService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }
}
