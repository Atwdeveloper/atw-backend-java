package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MinimumWageRateRepository extends JpaRepository<MinimumWageRate, UUID>, JpaSpecificationExecutor<MinimumWageRate> {

    /** Matches the partial unique index (ACTIVE rows only) - see V20's migration comment. */
    boolean existsByStateIdAndSkillCategoryIdAndEffectiveFromAndStatus(
            UUID stateId, UUID skillCategoryId, LocalDate effectiveFrom, ActiveStatus status);

    /**
     * The applicable version as of a date: the latest active row whose effective_from has arrived.
     * Unrelated to the admin list() endpoint's filtering - this resolves a single currently-in-force
     * rate (used by {@code MinimumWageRateLookupServiceImpl} and the {@code GET .../effective}
     * endpoint), not a filtered page of history rows.
     */
    Optional<MinimumWageRate> findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID stateId, UUID skillCategoryId, ActiveStatus status, LocalDate effectiveFrom);

    // The old findByStateId/findBySkillCategoryId/findByStateIdAndSkillCategoryId/findAll 4-way
    // branch used by MinimumWageRateService.list() is gone - replaced by JpaSpecificationExecutor
    // above, built via MinimumWageRateSpecifications + Specifications.allOf(). Confirmed unused
    // elsewhere (grepped the whole src/main tree) before removal, per this module's lookup-service gotcha.
}
