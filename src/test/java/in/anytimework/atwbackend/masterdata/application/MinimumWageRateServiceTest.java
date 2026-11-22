package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.domain.MinimumWageRateRepository;
import in.anytimework.atwbackend.masterdata.domain.SkillCategoryRepository;
import in.anytimework.atwbackend.masterdata.domain.StateRepository;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Clock-dependent "default to today" behavior (no Spring, no real DB) - a fixed
 * instant straddling the UTC/IST date boundary is used deliberately, to guard against a regression
 * back to a UTC-only (or system-default-zone) notion of "today" for this India-only business.
 */
class MinimumWageRateServiceTest {

    // 2026-03-31T20:00:00Z is 2026-04-01 01:30 IST (UTC+5:30) - UTC and IST disagree on the date.
    private static final Instant UTC_MARCH_31_IST_APRIL_1 = Instant.parse("2026-03-31T20:00:00Z");
    private static final Clock IST_CLOCK = Clock.fixed(UTC_MARCH_31_IST_APRIL_1, ZoneId.of("Asia/Kolkata"));

    private final MinimumWageRateRepository rates = mock(MinimumWageRateRepository.class);
    private final StateRepository states = mock(StateRepository.class);
    private final SkillCategoryRepository skillCategories = mock(SkillCategoryRepository.class);

    @Test
    void resolveEffective_withNullAsOf_usesTheClocksZoneNotUtc() {
        MinimumWageRateService service = new MinimumWageRateService(rates, states, skillCategories, IST_CLOCK);
        UUID stateId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveEffective(stateId, categoryId, null))
                .isInstanceOf(ResourceNotFoundException.class);

        // The repository must have been asked about 2026-04-01 (the IST calendar date for this
        // instant) - not 2026-03-31, which is what a UTC-zoned clock would have produced.
        verify(rates).findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                eq(stateId), eq(categoryId), eq(ActiveStatus.ACTIVE), eq(LocalDate.of(2026, 4, 1)));
    }

    @Test
    void resolveEffective_withExplicitAsOf_ignoresTheClockEntirely() {
        MinimumWageRateService service = new MinimumWageRateService(rates, states, skillCategories, IST_CLOCK);
        UUID stateId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        LocalDate explicitDate = LocalDate.of(2020, 1, 1);
        when(rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveEffective(stateId, categoryId, explicitDate))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(rates).findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                eq(stateId), eq(categoryId), eq(ActiveStatus.ACTIVE), eq(explicitDate));
    }

    @Test
    void create_rejectsADuplicateActiveVersionForTheSameEffectiveDate() {
        MinimumWageRateService service = new MinimumWageRateService(rates, states, skillCategories, IST_CLOCK);
        UUID stateId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        LocalDate effectiveFrom = LocalDate.of(2026, 4, 1);
        when(states.existsById(stateId)).thenReturn(true);
        when(skillCategories.existsById(categoryId)).thenReturn(true);
        when(rates.existsByStateIdAndSkillCategoryIdAndEffectiveFromAndStatus(
                stateId, categoryId, effectiveFrom, ActiveStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateMinimumWageRateRequest(stateId, categoryId, new BigDecimal("400.00"), effectiveFrom)))
                .hasMessageContaining("already exists");
    }
}
