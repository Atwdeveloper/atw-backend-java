package in.anytimework.atwbackend.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyAddressRepository extends JpaRepository<CompanyAddress, UUID> {

    List<CompanyAddress> findByCompanyId(UUID companyId);

    /** Every address row for the given companies, in one query - for a future batch-resolved list/export page, mirroring worker.domain.WorkerAddressRepository.findByWorkerIdIn. */
    List<CompanyAddress> findByCompanyIdIn(Collection<UUID> companyIds);

    Optional<CompanyAddress> findByCompanyIdAndPrimaryTrue(UUID companyId);

    /** The earliest-created surviving address for a company - used by {@code CompanyAddressService.delete} to auto-promote a replacement primary when the current primary is soft-deleted. */
    Optional<CompanyAddress> findFirstByCompanyIdOrderByCreatedAtAsc(UUID companyId);
}
