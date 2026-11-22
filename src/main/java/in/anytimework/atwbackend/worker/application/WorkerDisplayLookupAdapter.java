package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.faceauth.application.WorkerDisplayInfo;
import in.anytimework.atwbackend.faceauth.application.WorkerDisplayLookup;
import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This module's side of {@link WorkerDisplayLookup} - see that interface's Javadoc for why worker
 * supplies the implementation of a type it does not itself declare. A soft-deleted worker is
 * silently absent from the result ({@code WorkerRepository}'s own {@code @SQLRestriction}), same as
 * any other unknown id - callers must already tolerate that per the interface contract.
 */
@Component
class WorkerDisplayLookupAdapter implements WorkerDisplayLookup {

    private final WorkerRepository workers;

    WorkerDisplayLookupAdapter(WorkerRepository workers) {
        this.workers = workers;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, WorkerDisplayInfo> findByIds(Set<UUID> workerIds) {
        if (workerIds.isEmpty()) {
            return Map.of();
        }
        return workers.findAllById(workerIds).stream()
                .collect(Collectors.toMap(Worker::getId, WorkerDisplayLookupAdapter::toDisplayInfo));
    }

    private static WorkerDisplayInfo toDisplayInfo(Worker worker) {
        String fullName = worker.getMiddleName() == null || worker.getMiddleName().isBlank()
                ? worker.getFirstName() + " " + worker.getLastName()
                : worker.getFirstName() + " " + worker.getMiddleName() + " " + worker.getLastName();
        return new WorkerDisplayInfo(fullName, worker.getWorkerCode());
    }
}
