package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerAddressRepository extends JpaRepository<WorkerAddress, UUID> {

    Optional<WorkerAddress> findByWorkerIdAndAddressType(UUID workerId, AddressType addressType);

    /** Every address row (both types) for the given workers, in one query - used to batch-resolve city/state names for a list/export page. */
    List<WorkerAddress> findByWorkerIdIn(Collection<UUID> workerIds);
}
