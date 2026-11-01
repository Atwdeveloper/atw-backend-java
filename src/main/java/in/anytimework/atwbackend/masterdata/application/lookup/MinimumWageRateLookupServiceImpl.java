package in.anytimework.atwbackend.masterdata.application.lookup;

import in.anytimework.atwbackend.masterdata.domain.MinimumWageRateRepository;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class MinimumWageRateLookupServiceImpl implements MinimumWageRateLookupService {

    private final MinimumWageRateRepository rates;
    private final Clock clock;

    MinimumWageRateLookupServiceImpl(MinimumWageRateRepository rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    @Override
    public Optional<MinimumWageRateSummary> resolve(UUID stateId, UUID skillCategoryId, LocalDate asOf) {
        // null asOf means "today", same convention as MinimumWageRateService.resolveEffective() -
        // without this, effective_from <= NULL is SQL-UNKNOWN for every row, so the query would
        // silently return zero rows, indistinguishable from "no rate configured at all".
        LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now(clock);
        return rates.findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        stateId, skillCategoryId, ActiveStatus.ACTIVE, effectiveAsOf)
                .map(r -> new MinimumWageRateSummary(r.getId(), r.getMinDailyRate(), r.getEffectiveFrom()));
    }
}
