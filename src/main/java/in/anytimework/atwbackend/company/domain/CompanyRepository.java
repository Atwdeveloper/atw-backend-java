package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, java.util.UUID>, JpaSpecificationExecutor<Company> {

    /** Only ever true for a non-deleted company - {@link Company}'s {@code @SQLRestriction} applies here too. Pre-check for a friendlier 409 than waiting for the DB partial unique index; see CompanyService.create's Javadoc. */
    boolean existsByGstNumberHash(String gstNumberHash);

    boolean existsByPanNumberHash(String panNumberHash);

    /** Atomic, gap-tolerant source for the human-facing company_code - see CompanyService.generateCompanyCode. */
    @Query(value = "SELECT nextval('company_code_seq')", nativeQuery = true)
    long nextCompanyCodeSequenceValue();
}
