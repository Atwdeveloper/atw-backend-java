package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkerAddressRepository extends JpaRepository<WorkerAddress, UUID> {

    Optional<WorkerAddress> findByWorkerIdAndAddressType(UUID workerId, AddressType addressType);
}
