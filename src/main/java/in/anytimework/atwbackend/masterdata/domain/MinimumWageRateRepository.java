package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * The id of the single current row (same "latest ACTIVE row with effective_from &lt;= asOf" rule as
     * {@link #findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc}
     * above) for EVERY (state, skill category) pair at once - backs the admin "current rate per pair"
     * listing, as opposed to that single-pair method. {@code DISTINCT ON} has no JPQL/HQL equivalent,
     * hence native. Its {@code ORDER BY} is column-for-column the same as the partial unique index
     * {@code uq_minimum_wage_rate_state_category_effective_active (state_id, skill_category_id,
     * effective_from) WHERE status='ACTIVE'} (see V20's migration comment, which already calls out
     * this exact column order as serving "the latest effective version" query pattern) - Postgres can
     * walk that index directly (forward on state_id/skill_category_id, backward on effective_from
     * within each group) rather than sorting the whole table.
     */
    @Query(value = """
            SELECT DISTINCT ON (state_id, skill_category_id) id
            FROM minimum_wage_rate
            WHERE status = 'ACTIVE' AND effective_from <= :asOf
            ORDER BY state_id, skill_category_id, effective_from DESC
            """, nativeQuery = true)
    List<UUID> findCurrentRateIds(@Param("asOf") LocalDate asOf);

    // The old findByStateId/findBySkillCategoryId/findByStateIdAndSkillCategoryId/findAll 4-way
    // branch used by MinimumWageRateService.list() is gone - replaced by JpaSpecificationExecutor
    // above, built via MinimumWageRateSpecifications + Specifications.allOf(). Confirmed unused
    // elsewhere (grepped the whole src/main tree) before removal, per this module's lookup-service gotcha.
}
