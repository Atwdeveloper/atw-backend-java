package in.anytimework.atwbackend.company.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyStatusHistoryRepository extends JpaRepository<CompanyStatusHistory, UUID> {

    Page<CompanyStatusHistory> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
}
