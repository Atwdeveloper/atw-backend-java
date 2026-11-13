package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.document.application.DocumentService;
import in.anytimework.atwbackend.faceauth.application.FaceAuthService;
import in.anytimework.atwbackend.faceauth.application.dto.FaceEnrollmentDto;
import in.anytimework.atwbackend.kyc.application.KycService;
import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.kyc.application.KycVerificationCompletionResult;
import in.anytimework.atwbackend.masterdata.application.lookup.DepartmentLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.DepartmentSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.DesignationSummary;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.SkillSummary;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.persistence.SortValidation;
import in.anytimework.atwbackend.shared.persistence.Specifications;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerDesignationsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerSkillsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerDesignationRef;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSkillRef;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatusHistoryResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.domain.Worker;
import in.anytimework.atwbackend.worker.domain.WorkerDesignation;
import in.anytimework.atwbackend.worker.domain.WorkerDesignationRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import in.anytimework.atwbackend.worker.domain.WorkerSkill;
import in.anytimework.atwbackend.worker.domain.WorkerSkillRepository;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import in.anytimework.atwbackend.worker.domain.WorkerStatusHistory;
import in.anytimework.atwbackend.worker.domain.WorkerStatusHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Worker core profile: create/update, designation/skill assignment, status lifecycle, search/list,
 * bulk status/delete, CSV export. Address/bank-detail/notes live in their own sibling services
 * ({@link WorkerAddressService}, {@link WorkerBankDetailService}, {@link WorkerNoteService}),
 * matching how the identity module splits {@code AdminStaffService} from its own address/KYC/
 * bank-detail services. Authorization ({@code workers.view/create/edit/toggle_status/delete/export})
 * is enforced at the controller.
 */
@Service
public class WorkerService {

    private static final int MAX_DESIGNATIONS = 3;
    private static final Duration PHOTO_URL_VALIDITY = Duration.ofMinutes(15);
    private static final Set<WorkerStatus> REASON_REQUIRED_STATUSES =
            EnumSet.of(WorkerStatus.SUSPENDED, WorkerStatus.BLACKLISTED, WorkerStatus.INACTIVE);
    /** Reached only automatically (worker creation / KYC submission) - never a valid admin-chosen target via updateStatus. */
    private static final Set<WorkerStatus> NOT_DIRECTLY_SETTABLE =
            EnumSet.of(WorkerStatus.REGISTERED, WorkerStatus.KYC_PENDING);
    private static final String[] CSV_HEADER = {
            "Worker Code", "First Name", "Last Name", "Mobile Number", "Status", "KYC Status", "Designation", "Department", "Face Enrolled"
    };
    /**
     * Real, directly queryable scalar columns only - see {@link SortValidation}'s Javadoc for why.
     * Notably excludes {@code kycStatus} and {@code faceEnrolledAt} (each a different module's table,
     * not a column here at all) and every batch-resolved display name ({@code primaryDesignationName},
     * department/designation names) - sorting by those would need a cross-module or cross-table join
     * this codebase deliberately avoids at the query level.
     */
    private static final Set<String> ALLOWED_SORT =
            Set.of("firstName", "lastName", "workerCode", "mobileNumber", "status", "createdAt");

    private final WorkerRepository workers;
    private final WorkerDesignationRepository workerDesignations;
    private final WorkerSkillRepository workerSkills;
    private final WorkerStatusHistoryRepository statusHistory;
    private final DepartmentLookupService departmentLookup;
    private final IndustryLookupService industryLookup;
    private final DesignationLookupService designationLookup;
    private final SkillLookupService skillLookup;
    private final KycService kycService;
    private final FaceAuthService faceAuthService;
    private final DocumentService documentService;
    private final WorkerProfilePhotoSupport photoSupport;
    private final CurrentAdmin currentAdmin;
    private final Clock clock;

    public WorkerService(WorkerRepository workers, WorkerDesignationRepository workerDesignations,
                         WorkerSkillRepository workerSkills, WorkerStatusHistoryRepository statusHistory,
                         DepartmentLookupService departmentLookup, IndustryLookupService industryLookup,
                         DesignationLookupService designationLookup, SkillLookupService skillLookup,
                         KycService kycService, FaceAuthService faceAuthService, DocumentService documentService,
                         WorkerProfilePhotoSupport photoSupport, CurrentAdmin currentAdmin, Clock clock) {
        this.workers = workers;
        this.workerDesignations = workerDesignations;
        this.workerSkills = workerSkills;
        this.statusHistory = statusHistory;
        this.departmentLookup = departmentLookup;
        this.industryLookup = industryLookup;
        this.designationLookup = designationLookup;
        this.skillLookup = skillLookup;
        this.kycService = kycService;
        this.faceAuthService = faceAuthService;
        this.documentService = documentService;
        this.photoSupport = photoSupport;
        this.currentAdmin = currentAdmin;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkerResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerStatusHistoryResponse> listStatusHistory(UUID id, Pageable pageable) {
        if (!workers.existsById(id)) {
            throw new ResourceNotFoundException("Worker not found");
        }
        return PageResponse.from(statusHistory.findByWorkerIdOrderByCreatedAtDesc(id, pageable)
                .map(h -> new WorkerStatusHistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(),
                        h.getReason(), h.getCreatedBy(), h.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerSummaryResponse> list(WorkerSearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        Optional<List<Specification<Worker>>> predicates = buildPredicates(criteria);
        if (predicates.isEmpty()) {
            // The kycStatus filter resolved to zero matching worker ids - no further query can match
            // anything, so short-circuit rather than ask the database to filter on an empty id set.
            return PageResponse.from(Page.empty(pageable));
        }
        Page<Worker> page = workers.findAll(Specifications.allOf(predicates.get()), pageable);
        List<WorkerSummaryResponse> summaries = toSummaryResponses(page.getContent());
        return new PageResponse<>(summaries, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    /**
     * Every column/subquery filter in {@code criteria} that is non-empty becomes one predicate,
     * ANDed together via {@link Specifications#allOf}. Shared by {@link #list} and
     * {@link #exportToCsv} so both honor the exact same filter semantics.
     *
     * <p>Returns {@link Optional#empty()} when the {@code kycStatus} filter itself resolves to zero
     * matching worker ids: {@code kyc} is a separate module, so that filter can only be applied as a
     * precomputed {@code idIn(...)} predicate (see {@link WorkerSpecifications#idIn}), and an empty
     * id set is deliberately never turned into a {@code Specification} (an empty SQL {@code IN ()} is
     * invalid) - the caller must treat "no possible match" as its own case instead.</p>
     */
    private Optional<List<Specification<Worker>>> buildPredicates(WorkerSearchCriteria criteria) {
        List<Specification<Worker>> predicates = new ArrayList<>();
        if (criteria.status() != null && !criteria.status().isEmpty()) {
            predicates.add(WorkerSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.departmentId() != null && !criteria.departmentId().isEmpty()) {
            predicates.add(WorkerSpecifications.hasDepartment(criteria.departmentId()));
        }
        if (criteria.industryId() != null && !criteria.industryId().isEmpty()) {
            predicates.add(WorkerSpecifications.hasIndustry(criteria.industryId()));
        }
        if (criteria.designationId() != null && !criteria.designationId().isEmpty()) {
            predicates.add(WorkerSpecifications.hasDesignation(criteria.designationId()));
        }
        if (criteria.skillId() != null && !criteria.skillId().isEmpty()) {
            predicates.add(WorkerSpecifications.hasSkill(criteria.skillId()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(WorkerSpecifications.matchesSearch(criteria.search()));
        }
        if (criteria.kycStatus() != null && !criteria.kycStatus().isEmpty()) {
            Set<UUID> matchingWorkerIds = kycService.findWorkerIdsByStatus(criteria.kycStatus());
            if (matchingWorkerIds.isEmpty()) {
                return Optional.empty();
            }
            predicates.add(WorkerSpecifications.idIn(matchingWorkerIds));
        }
        return Optional.of(predicates);
    }

    @Transactional
    public WorkerResponse create(CreateWorkerRequest request) {
        if (workers.existsByMobileNumber(request.mobileNumber())) {
            throw new ConflictException("A worker with this mobile number already exists.");
        }
        if (!departmentLookup.exists(request.departmentId())) {
            throw new ValidationException("Unknown department id.");
        }
        if (request.industryId() != null && !industryLookup.exists(request.industryId())) {
            throw new ValidationException("Unknown industry id.");
        }
        validateDesignationSelection(request.designationIds(), request.primaryDesignationId());
        validateSkillSelection(request.skillIds(), request.designationIds());

        Worker worker = new Worker(generateWorkerCode(), request.firstName(), request.middleName(), request.lastName(),
                request.gender(), request.dateOfBirth(), request.mobileNumber(), request.personalEmail(),
                request.departmentId(), request.industryId());
        worker = workers.save(worker);

        saveDesignations(worker.getId(), request.designationIds(), request.primaryDesignationId());
        saveSkills(worker.getId(), request.skillIds());
        statusHistory.save(new WorkerStatusHistory(worker.getId(), null, WorkerStatus.REGISTERED, null));

        if (request.aadhaarVerificationId() != null) {
            // Consumes the verification (see KycService.completeFromVerification's Javadoc); an
            // invalid/expired/already-used/not-yet-verified reference throws, rolling back this
            // entire transaction - worker creation fails outright rather than leaving behind a
            // worker whose requested Aadhaar backing never actually applied.
            KycVerificationCompletionResult kycResult =
                    kycService.completeFromVerification(worker.getId(), request.aadhaarVerificationId());
            if (kycResult.profilePhotoDocumentId() != null) {
                worker.setProfilePhotoDocumentId(kycResult.profilePhotoDocumentId());
                worker = workers.save(worker);
            }
        }

        return toResponse(worker);
    }

    @Transactional
    public WorkerResponse update(UUID id, UpdateWorkerRequest request) {
        Worker worker = findOrThrow(id);
        if (!request.mobileNumber().equals(worker.getMobileNumber())
                && workers.existsByMobileNumberAndIdNot(request.mobileNumber(), id)) {
            throw new ConflictException("A worker with this mobile number already exists.");
        }
        if (!departmentLookup.exists(request.departmentId())) {
            throw new ValidationException("Unknown department id.");
        }
        if (request.industryId() != null && !industryLookup.exists(request.industryId())) {
            throw new ValidationException("Unknown industry id.");
        }
        worker.updateProfile(request.firstName(), request.middleName(), request.lastName(), request.gender(),
                request.dateOfBirth(), request.mobileNumber(), request.personalEmail(),
                request.departmentId(), request.industryId());
        workers.save(worker);
        return toResponse(worker);
    }

    @Transactional
    public WorkerResponse updateStatus(UUID id, WorkerStatus newStatus, String reason) {
        Worker worker = applyStatusChange(id, newStatus, reason);
        return toResponse(worker);
    }

    /**
     * Bulk counterpart to {@link #updateStatus} - applies the same guarded transition to every id
     * independently, recording a per-id failure instead of aborting the whole batch. A single shared
     * {@code @Transactional} is safe here (unlike {@link #bulkDelete}): every exception
     * {@link #applyStatusChange} can throw ({@link ResourceNotFoundException},
     * {@link ValidationException}, {@link BusinessRuleException}) is a clean, pre-write check that
     * fails before any flush is attempted, so one failing id can never leave Hibernate's persistence
     * context rollback-only for the ids still to come - see {@code Specifications.allOf}'s sibling
     * class-level gotcha notes and {@code DepartmentService.bulkUpdateStatus}'s identical reasoning.
     */
    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, WorkerStatus status, String reason) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                applyStatusChange(id, status, reason);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            } catch (ValidationException | BusinessRuleException e) {
                failures.add(new BulkActionFailure(id, e.getMessage()));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    /**
     * Explicit target status, validated against a small set of rules rather than a full state-machine
     * framework (proportionate to the number of states involved). Shared guarded core for both
     * {@link #updateStatus} (which wraps it with {@link #toResponse}) and {@link #bulkUpdateStatus}
     * (which does not - assembling a full {@link WorkerResponse}, several extra lookups/queries, for
     * every item would be wasted work the bulk caller never uses).
     * <ul>
     *   <li>REGISTERED/KYC_PENDING are reached only automatically - never a valid target here.</li>
     *   <li>No-op transitions (newStatus == current status) are rejected rather than silently accepted.</li>
     *   <li>A reason is required for SUSPENDED/BLACKLISTED/INACTIVE.</li>
     *   <li>Entering OR leaving BLACKLISTED requires a super-admin - un-blacklisting is exactly as
     *       high-privilege as blacklisting, not a one-way gate (a real gap an earlier version of this
     *       method had: any holder of workers.toggle_status could silently reverse a super-admin's
     *       blacklist decision just by requesting ACTIVE).</li>
     *   <li>ACTIVE is not reachable directly from REGISTERED/KYC_PENDING via this admin endpoint - a
     *       worker must complete KYC (see {@link #activateAfterKycVerified}, which calls the private
     *       {@link #changeStatus} helper directly and is therefore unaffected by this restriction).</li>
     * </ul>
     */
    private Worker applyStatusChange(UUID id, WorkerStatus newStatus, String reason) {
        Worker worker = findOrThrow(id);
        WorkerStatus oldStatus = worker.getStatus();

        if (NOT_DIRECTLY_SETTABLE.contains(newStatus)) {
            throw new ValidationException(newStatus + " is reached automatically and cannot be set directly.");
        }
        if (oldStatus == newStatus) {
            throw new ValidationException("Worker is already " + newStatus + ".");
        }
        if (REASON_REQUIRED_STATUSES.contains(newStatus) && !StringUtils.hasText(reason)) {
            throw new ValidationException("A reason is required when setting status to " + newStatus + ".");
        }
        boolean touchesBlacklist = newStatus == WorkerStatus.BLACKLISTED || oldStatus == WorkerStatus.BLACKLISTED;
        if (touchesBlacklist && !currentAdmin.isSuperAdmin()) {
            throw new BusinessRuleException("Only a super-admin can blacklist a worker or reverse a blacklist.");
        }
        if (newStatus == WorkerStatus.ACTIVE && NOT_DIRECTLY_SETTABLE.contains(oldStatus)) {
            throw new BusinessRuleException(
                    "A worker must complete KYC before becoming ACTIVE; this happens automatically, not via this endpoint.");
        }

        changeStatus(worker, newStatus, reason);
        return worker;
    }

    /**
     * Called by {@code WorkerKycEventListener} when a worker's KYC first becomes VERIFIED.
     * Does nothing if the worker is already ACTIVE, or is SUSPENDED/BLACKLISTED - KYC verification
     * must never reactivate a worker an admin has deliberately put on hold or banned.
     */
    @Transactional
    public void activateAfterKycVerified(UUID workerId) {
        workers.findById(workerId).ifPresent(worker -> {
            if (worker.getStatus() == WorkerStatus.REGISTERED || worker.getStatus() == WorkerStatus.KYC_PENDING) {
                changeStatus(worker, WorkerStatus.ACTIVE, "KYC verified");
            }
        });
    }

    private void changeStatus(Worker worker, WorkerStatus newStatus, String reason) {
        WorkerStatus oldStatus = worker.getStatus();
        worker.changeStatus(newStatus, reason);
        workers.save(worker);
        statusHistory.save(new WorkerStatusHistory(worker.getId(), oldStatus, newStatus, reason));
    }

    /**
     * Soft-deletes a worker: sets {@code deletedAt} (via {@link Worker#markDeleted}) rather than ever
     * issuing a real SQL DELETE. {@link Worker}'s {@code @SQLRestriction("deleted_at is null")} then
     * makes this worker invisible to every existing query path - {@link #get}, {@link #list},
     * {@link #exportToCsv}, {@code existsByMobileNumber(AndIdNot)}, the pessimistic-lock JPQL in
     * {@code WorkerRepository.findByIdForUpdate} - automatically, with no other method in this class
     * needing to check a "deleted" flag itself. See {@code Worker}'s class-level Javadoc for why this
     * is correct for every one of those paths, not just the obvious ones.
     *
     * <p>Every one of this worker's own satellite rows (address, bank detail, designations, skills,
     * notes, status history, KYC profile, face-biometric enrollment) is left completely untouched -
     * nothing is cascaded away, preserving full history for audit/reconciliation (their
     * {@code ON DELETE CASCADE} FKs to {@code worker.id}, see {@code V22__worker.sql}/
     * {@code V23__kyc.sql}/{@code V25__faceauth.sql}, simply never fire via this path anymore). The
     * worker's mobile number becomes available again for a new worker to register with - a deliberate
     * product decision, see {@code V27__worker_soft_delete.sql}'s partial unique index, not an
     * oversight.</p>
     *
     * <p>Still refused unless the worker's current status is something other than ACTIVE - the caller
     * must change status first (e.g. to INACTIVE) via the already-guarded {@link #updateStatus}/
     * {@link #bulkUpdateStatus}. This guard is kept as a deliberate, independent safety check (a
     * conscious two-step before removing someone from the active roster) even though deletion no
     * longer destroys anything - it is not a consequence of the old hard-delete design.</p>
     *
     * <p>There is deliberately no restore/undelete endpoint yet - nothing is destroyed, so one can be
     * added later purely at the application layer, with no migration changes.</p>
     */
    @Transactional
    public void delete(UUID id) {
        Worker worker = findOrThrow(id);
        if (worker.getStatus() == WorkerStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot delete a worker while ACTIVE. Change their status first (e.g. to INACTIVE), then delete.");
        }
        worker.markDeleted(clock.instant());
        workers.save(worker);
    }

    /**
     * Bulk counterpart to {@link #delete} - applies the same guarded soft-delete to every id
     * independently, recording a per-id failure instead of aborting the whole batch. A single shared
     * {@code @Transactional} is safe here (unlike this method's hard-delete predecessor, which needed
     * a {@code TransactionTemplate}/{@code PROPAGATION_REQUIRES_NEW} split per id - see
     * {@code DepartmentService.bulkDelete}'s Javadoc for why a masterdata entity's delete still needs
     * that): every exception {@link #delete} can throw ({@link ResourceNotFoundException},
     * {@link BusinessRuleException}) is a clean, pre-write check that fails before any flush is
     * attempted, and a soft-delete is a plain UPDATE that can never throw a
     * {@code DataIntegrityViolationException} from an FK constraint - so there is no failed-flush
     * poisoning a shared persistence context for the ids still to come, the hazard that machinery
     * existed to isolate against.
     */
    @Transactional
    public BulkActionResult bulkDelete(Set<UUID> ids) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                delete(id);
                successCount++;
            } catch (ResourceNotFoundException e) {
                failures.add(new BulkActionFailure(id, "Not found"));
            } catch (BusinessRuleException e) {
                failures.add(new BulkActionFailure(id, e.getMessage()));
            }
        }
        return new BulkActionResult(successCount, failures);
    }

    /**
     * CSV export for the admin worker list. Exports exactly {@code ids} when given and non-empty
     * (unknown ids are silently skipped, same as a plain {@code findAllById} would); otherwise falls
     * back to every worker matching {@code criteria} (the exact same filter semantics as {@link #list},
     * via the shared {@link #buildPredicates}, just unpaged). Plain {@link StringBuilder}/byte output,
     * not Apache POI: a CSV is a flat table with no formatting requirement, so a heavier Excel-writing
     * dependency buys nothing here and this streams more cheaply for a potentially large export.
     *
     * <p>Builds the whole CSV in memory rather than streaming row-by-row to the HTTP response: simple
     * and correct for this feature's current scale (an admin tool, no attendance/payroll volume yet),
     * called out here as a known, deliberate limitation rather than a true database-cursor export - if
     * the worker table grows to a size where that matters, this should move to a streaming response
     * instead of a larger in-memory buffer.</p>
     */
    @Transactional(readOnly = true)
    public byte[] exportToCsv(Set<UUID> ids, WorkerSearchCriteria criteria) {
        List<Worker> workerList = (ids != null && !ids.isEmpty())
                ? workers.findAllById(ids)
                : buildPredicates(criteria).map(predicates -> workers.findAll(Specifications.allOf(predicates))).orElse(List.of());
        return toCsv(workerList);
    }

    private byte[] toCsv(List<Worker> workerList) {
        Set<UUID> workerIds = workerList.stream().map(Worker::getId).collect(Collectors.toSet());
        Map<UUID, UUID> primaryDesignationIdByWorker = primaryDesignationIdByWorker(workerIds);
        Map<UUID, String> designationNameById = designationNameById();
        Map<UUID, String> departmentNameById = departmentNameById();
        Map<UUID, KycStatus> kycStatusByWorker = kycService.getStatuses(workerIds);
        Map<UUID, Instant> faceEnrolledAtByWorker = faceAuthService.getEnrollmentDates(workerIds);

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", CSV_HEADER)).append("\r\n");
        for (Worker w : workerList) {
            UUID primaryDesignationId = primaryDesignationIdByWorker.get(w.getId());
            String designationName = primaryDesignationId == null ? null : designationNameById.get(primaryDesignationId);
            String departmentName = departmentNameById.get(w.getDepartmentId());
            KycStatus kycStatus = kycStatusByWorker.getOrDefault(w.getId(), KycStatus.NOT_STARTED);
            boolean faceEnrolled = faceEnrolledAtByWorker.containsKey(w.getId());
            csv.append(csvField(w.getWorkerCode())).append(',')
                    .append(csvField(w.getFirstName())).append(',')
                    .append(csvField(w.getLastName())).append(',')
                    .append(csvField(w.getMobileNumber())).append(',')
                    .append(csvField(w.getStatus().name())).append(',')
                    .append(csvField(kycStatus.name())).append(',')
                    .append(csvField(designationName)).append(',')
                    .append(csvField(departmentName)).append(',')
                    .append(csvField(faceEnrolled ? "Yes" : "No")).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Quotes a field only when it contains a character that would otherwise break the CSV format (RFC 4180). */
    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public WorkerResponse assignDesignations(UUID id, AssignWorkerDesignationsRequest request) {
        // Pessimistic lock: serializes this full-replace against a concurrent one for the same
        // worker - see WorkerRepository.findByIdForUpdate's Javadoc for why a plain findById isn't enough here.
        Worker worker = findOrThrowForUpdate(id);
        validateDesignationSelection(request.designationIds(), request.primaryDesignationId());

        // Any skill not belonging to the new designation set must be dropped, same as legacy enforced.
        Set<UUID> survivingSkillIds = workerSkills.findByWorkerId(id).stream()
                .map(WorkerSkill::getSkillId)
                .filter(skillId -> request.designationIds().contains(skillLookup.get(skillId).designationId()))
                .collect(Collectors.toSet());

        workerDesignations.deleteByWorkerId(id);
        workerDesignations.flush();
        saveDesignations(id, request.designationIds(), request.primaryDesignationId());

        workerSkills.deleteByWorkerId(id);
        workerSkills.flush();
        saveSkills(id, survivingSkillIds);

        return toResponse(worker);
    }

    @Transactional
    public WorkerResponse assignSkills(UUID id, AssignWorkerSkillsRequest request) {
        Worker worker = findOrThrowForUpdate(id);
        Set<UUID> currentDesignationIds = workerDesignations.findByWorkerId(id).stream()
                .map(WorkerDesignation::getDesignationId).collect(Collectors.toSet());
        validateSkillSelection(request.skillIds(), currentDesignationIds);

        workerSkills.deleteByWorkerId(id);
        workerSkills.flush();
        saveSkills(id, request.skillIds());

        return toResponse(worker);
    }

    /**
     * Completes an already-existing worker's KYC from a successful Aadhaar e-KYC verification - the
     * same mechanism {@link #create} uses when {@code aadhaarVerificationId} is given at creation
     * time, exposed separately for a worker who was onboarded manually (e.g. via {@code submitAadhaar})
     * and now wants to upgrade that unverified entry to a live, API-verified one.
     */
    @Transactional
    public WorkerResponse completeAadhaarVerification(UUID id, UUID verificationId) {
        Worker worker = findOrThrow(id);
        KycVerificationCompletionResult kycResult = kycService.completeFromVerification(id, verificationId);
        if (kycResult.profilePhotoDocumentId() != null) {
            worker.setProfilePhotoDocumentId(kycResult.profilePhotoDocumentId());
            workers.save(worker);
        }
        return toResponse(worker);
    }

    @Transactional
    public WorkerResponse uploadProfilePhoto(UUID id, String filename, String contentType, byte[] content) {
        Worker worker = findOrThrow(id);
        UUID newDocumentId = photoSupport.replace(id, worker.getProfilePhotoDocumentId(), filename, contentType, content);
        worker.setProfilePhotoDocumentId(newDocumentId);
        workers.save(worker);
        return toResponse(worker);
    }

    @Transactional
    public WorkerResponse removeProfilePhoto(UUID id) {
        Worker worker = findOrThrow(id);
        photoSupport.remove(worker.getProfilePhotoDocumentId());
        worker.setProfilePhotoDocumentId(null);
        workers.save(worker);
        return toResponse(worker);
    }

    private void validateDesignationSelection(Set<UUID> designationIds, UUID primaryDesignationId) {
        if (designationIds.isEmpty()) {
            throw new ValidationException("At least one designation must be selected.");
        }
        if (designationIds.size() > MAX_DESIGNATIONS) {
            throw new ValidationException("A worker may hold at most " + MAX_DESIGNATIONS + " designations.");
        }
        if (!designationIds.contains(primaryDesignationId)) {
            throw new ValidationException("primaryDesignationId must be one of designationIds.");
        }
        for (UUID designationId : designationIds) {
            if (!designationLookup.exists(designationId)) {
                throw new ValidationException("Unknown designation id: " + designationId);
            }
        }
    }

    private void validateSkillSelection(Set<UUID> skillIds, Set<UUID> designationIds) {
        for (UUID skillId : skillIds) {
            if (!skillLookup.exists(skillId)) {
                throw new ValidationException("Unknown skill id: " + skillId);
            }
            SkillSummary skill = skillLookup.get(skillId);
            if (!designationIds.contains(skill.designationId())) {
                throw new ValidationException(
                        "Skill '" + skill.name() + "' does not belong to any of the selected designations.");
            }
        }
    }

    private void saveDesignations(UUID workerId, Set<UUID> designationIds, UUID primaryDesignationId) {
        List<WorkerDesignation> rows = designationIds.stream()
                .map(designationId -> new WorkerDesignation(workerId, designationId, designationId.equals(primaryDesignationId)))
                .toList();
        workerDesignations.saveAll(rows);
    }

    private void saveSkills(UUID workerId, Set<UUID> skillIds) {
        workerSkills.saveAll(skillIds.stream().map(skillId -> new WorkerSkill(workerId, skillId)).toList());
    }

    private String generateWorkerCode() {
        return "WRK" + String.format("%07d", workers.nextWorkerCodeSequenceValue());
    }

    private Worker findOrThrow(UUID id) {
        return workers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
    }

    private Worker findOrThrowForUpdate(UUID id) {
        return workers.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
    }

    private List<WorkerSummaryResponse> toSummaryResponses(List<Worker> workerList) {
        if (workerList.isEmpty()) {
            return List.of();
        }
        Set<UUID> workerIds = workerList.stream().map(Worker::getId).collect(Collectors.toSet());
        Map<UUID, UUID> primaryDesignationIdByWorker = primaryDesignationIdByWorker(workerIds);
        Map<UUID, String> designationNameById = designationNameById();
        Map<UUID, KycStatus> kycStatusByWorker = kycService.getStatuses(workerIds);
        Map<UUID, Instant> faceEnrolledAtByWorker = faceAuthService.getEnrollmentDates(workerIds);

        return workerList.stream().map(w -> {
            UUID primaryDesignationId = primaryDesignationIdByWorker.get(w.getId());
            String primaryDesignationName = primaryDesignationId == null ? null : designationNameById.get(primaryDesignationId);
            return new WorkerSummaryResponse(w.getId(), w.getWorkerCode(), w.getFirstName(), w.getLastName(),
                    w.getMobileNumber(), primaryDesignationName, w.getStatus(),
                    kycStatusByWorker.getOrDefault(w.getId(), KycStatus.NOT_STARTED),
                    w.getProfilePhotoDocumentId() != null, faceEnrolledAtByWorker.get(w.getId()));
        }).toList();
    }

    /** Batch-loaded (one query for the whole page/export, never per row) - worker id -> its primary designation id. */
    private Map<UUID, UUID> primaryDesignationIdByWorker(Set<UUID> workerIds) {
        if (workerIds.isEmpty()) {
            return Map.of();
        }
        return workerDesignations.findByWorkerIdInAndPrimaryTrue(workerIds).stream()
                .collect(Collectors.toMap(WorkerDesignation::getWorkerId, WorkerDesignation::getDesignationId));
    }

    /** Batch-loaded (the full, small masterdata list, never per row) - designation id -> its display name. */
    private Map<UUID, String> designationNameById() {
        return designationLookup.list().stream().collect(Collectors.toMap(DesignationSummary::id, DesignationSummary::name));
    }

    /** Batch-loaded (the full, small masterdata list, never per row) - department id -> its display name. */
    private Map<UUID, String> departmentNameById() {
        return departmentLookup.list().stream().collect(Collectors.toMap(DepartmentSummary::id, DepartmentSummary::name));
    }

    private WorkerResponse toResponse(Worker worker) {
        List<WorkerDesignationRef> designationRefs = workerDesignations.findByWorkerId(worker.getId()).stream()
                .map(wd -> new WorkerDesignationRef(wd.getDesignationId(), designationLookup.get(wd.getDesignationId()).name(), wd.isPrimary()))
                .sorted(Comparator.comparing(WorkerDesignationRef::primary).reversed().thenComparing(WorkerDesignationRef::name))
                .toList();
        List<WorkerSkillRef> skillRefs = workerSkills.findByWorkerId(worker.getId()).stream()
                .map(ws -> new WorkerSkillRef(ws.getSkillId(), skillLookup.get(ws.getSkillId()).name()))
                .sorted(Comparator.comparing(WorkerSkillRef::name))
                .toList();
        String photoUrl = worker.getProfilePhotoDocumentId() == null ? null
                : documentService.generateDownloadUrl(worker.getProfilePhotoDocumentId(), PHOTO_URL_VALIDITY).toString();
        KycStatus kycStatus = kycService.getStatus(worker.getId());
        FaceEnrollmentDto faceEnrollment = faceAuthService.getEnrollment(worker.getId());

        return new WorkerResponse(worker.getId(), worker.getWorkerCode(), worker.getFirstName(), worker.getMiddleName(),
                worker.getLastName(), worker.getGender(), worker.getDateOfBirth(), worker.getMobileNumber(),
                worker.getPersonalEmail(), worker.getDepartmentId(), worker.getIndustryId(), worker.getStatus(),
                worker.getStatusReason(), photoUrl, designationRefs, skillRefs, kycStatus, faceEnrollment,
                worker.getCreatedAt());
    }
}
