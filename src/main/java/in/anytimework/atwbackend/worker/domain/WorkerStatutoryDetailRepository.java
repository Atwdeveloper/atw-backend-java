package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkerStatutoryDetailRepository extends JpaRepository<WorkerStatutoryDetail, UUID> {

    Optional<WorkerStatutoryDetail> findByWorkerId(UUID workerId);

    boolean existsByUanNumberHashAndWorkerIdNot(String uanNumberHash, UUID workerId);

    boolean existsByEsicNumberHashAndWorkerIdNot(String esicNumberHash, UUID workerId);
}
