package in.anytimework.atwbackend.masterdata.domain;

import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MinimumWageRateRepository extends JpaRepository<MinimumWageRate, UUID> {

    /** Matches the partial unique index (ACTIVE rows only) - see V20's migration comment. */
    boolean existsByStateIdAndSkillCategoryIdAndEffectiveFromAndStatus(
            UUID stateId, UUID skillCategoryId, LocalDate effectiveFrom, ActiveStatus status);

    /** The applicable version as of a date: the latest active row whose effective_from has arrived. */
    Optional<MinimumWageRate> findFirstByStateIdAndSkillCategoryIdAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            UUID stateId, UUID skillCategoryId, ActiveStatus status, LocalDate effectiveFrom);

    // Paged, not List-returning: unlike every other reference table in this module, this one is
    // append-only and grows with every rate change over the years, never bounded by how many
    // distinct real-world entities exist - see MinimumWageRateService.list()'s Javadoc.
    Page<MinimumWageRate> findByStateId(UUID stateId, Pageable pageable);

    Page<MinimumWageRate> findBySkillCategoryId(UUID skillCategoryId, Pageable pageable);

    /** Full version history for one (state, skill category) pair, paged. */
    Page<MinimumWageRate> findByStateIdAndSkillCategoryId(UUID stateId, UUID skillCategoryId, Pageable pageable);
}
