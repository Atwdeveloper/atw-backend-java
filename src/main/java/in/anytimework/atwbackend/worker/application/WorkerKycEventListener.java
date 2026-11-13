package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.kyc.application.KycVerifiedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to a worker's KYC first becoming verified by auto-advancing their status - the first
 * genuinely cross-module domain event in this codebase (identity's own calls into notification are
 * direct method calls, not events). {@code @ApplicationModuleListener} = run after the publishing
 * transaction commits, asynchronously, tracked by Spring Modulith's event publication registry (the
 * same transactional outbox already used for {@code notification}'s email sends), so this is never
 * lost even if the process crashes before it runs.
 */
@Component
class WorkerKycEventListener {

    private final WorkerService workerService;

    WorkerKycEventListener(WorkerService workerService) {
        this.workerService = workerService;
    }

    @ApplicationModuleListener
    void on(KycVerifiedEvent event) {
        workerService.activateAfterKycVerified(event.workerId());
    }
}
