package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.MinimumWageRateRepository;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cross-module resolve() entry point (no Spring, no real DB) - specifically that
 * a null asOf defaults to "today" the same way the admin-facing MinimumWageRateService does, rather
 * than silently binding effective_from &lt;= NULL (which SQL three-valued logic makes false for every
 * row, indistinguishable from "nothing configured").
 */
class MinimumWageRateLookupServiceImplTest {

    private static final Clock IST_CLOCK =
            Clock.fixed(Instant.parse("2026-03-31T20:00:00Z"), ZoneId.of("Asia/Kolkata")); // 2026-04-01 IST

    private final MinimumWageRateRepository rates = mock(MinimumWageRateRepository.class);
    private final MinimumWageRateLookupServiceImpl service = new MinimumWageRateLookupServiceImpl(rates, IST_CLOCK);

    @Test
    void resolve_withNullAsOf_defaultsToTodayInTheClocksZone() {
        UUID stateId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        Optional<MinimumWageRateSummary> result = service.resolve(stateId, categoryId, null);

        assertThat(result).isEmpty();
        verify(rates).findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                eq(stateId), eq(categoryId), eq(ActiveStatus.ACTIVE), eq(LocalDate.of(2026, 4, 1)));
    }

    @Test
    void resolve_withExplicitAsOf_passesItThroughUnchanged() {
        UUID stateId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        LocalDate explicitDate = LocalDate.of(2020, 6, 15);
        when(rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        service.resolve(stateId, categoryId, explicitDate);

        verify(rates).findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                eq(stateId), eq(categoryId), eq(ActiveStatus.ACTIVE), eq(explicitDate));
    }
}
