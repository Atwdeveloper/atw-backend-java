package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkerBankDetailRepository extends JpaRepository<WorkerBankDetail, UUID> {

    Optional<WorkerBankDetail> findByWorkerId(UUID workerId);
}
