package in.anytimework.atwbackend.company.application;

import in.anytimework.atwbackend.company.domain.Company;
import in.anytimework.atwbackend.company.domain.CompanyAddress;
import in.anytimework.atwbackend.company.domain.CompanyAddressRepository;
import in.anytimework.atwbackend.company.domain.CompanyAuthToken;
import in.anytimework.atwbackend.company.domain.CompanyAuthTokenRepository;
import in.anytimework.atwbackend.company.domain.CompanyAuthTokenType;
import in.anytimework.atwbackend.company.domain.CompanyRefreshTokenRepository;
import in.anytimework.atwbackend.company.domain.CompanyRepository;
import in.anytimework.atwbackend.company.domain.CompanyStatus;
import in.anytimework.atwbackend.company.domain.CompanyStatusHistory;
import in.anytimework.atwbackend.company.domain.CompanyStatusHistoryRepository;
import in.anytimework.atwbackend.company.domain.CompanyUser;
import in.anytimework.atwbackend.company.domain.CompanyUserRepository;
import in.anytimework.atwbackend.company.domain.CompanyUserRole;
import in.anytimework.atwbackend.company.domain.CompanyUserStatus;
import in.anytimework.atwbackend.identity.application.lookup.AdminActorLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.masterdata.application.lookup.IndustryLookupService;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.api.BulkActionFailure;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActorRefs;
import in.anytimework.atwbackend.shared.persistence.SortValidation;
import in.anytimework.atwbackend.shared.persistence.Specifications;
import in.anytimework.atwbackend.shared.security.CompanyIdentityNumberHasher;
import in.anytimework.atwbackend.shared.security.OpaqueTokens;
import in.anytimework.atwbackend.shared.security.PermissionChecker;
import in.anytimework.atwbackend.shared.security.PiiMasking;
import in.anytimework.atwbackend.shared.security.jwt.CurrentAdmin;
import in.anytimework.atwbackend.shared.export.ExcelWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Company lifecycle: create (with full owner-account provisioning), update, get, list, soft delete,
 * approval-status transitions, and GST-verification triggering. Authorization
 * ({@code company.view/create/edit/delete/manage_status/view_sensitive/export}) is enforced at the
 * controller (part 2 of this module), exactly matching how {@code WorkerService}/{@code AdminStaffService}
 * leave {@code @PreAuthorize} entirely to their own controllers - except {@code company.view_sensitive},
 * which (like {@code kyc.view_sensitive}) this service checks itself via {@link PermissionChecker} to
 * decide whether to mask GST/PAN in its response, mirroring {@code KycServiceImpl.toDto}'s exact
 * precedent.
 *
 * <p><strong>Scope note (confirmed out of scope this round):</strong> no company documents, no
 * staff-user creation (only the OWNER row), no custom per-company permission profiles, no
 * multi-company-per-user. The company-user login/auth flow itself (accept-invite, login, OTP,
 * refresh, logout, forgot/reset password, the session-policy filter, JWT claims) is part 2/3 of this
 * module, not this class - this class only provisions the OWNER's account row and its invite token
 * so that flow has something to authenticate against later.</p>
 *
 * <p><strong>Open question for whoever builds the admin controller (part 2):</strong> address
 * management here is deliberately NOT bundled into {@link #update} (mirrors
 * {@code worker.application.WorkerAddressService} being a separate sibling service/sub-resource, not
 * methods on {@code WorkerService}) - only {@link #create} persists an initial address list. Confirm
 * this with the developer before wiring the admin API: a company's addresses are a variable-length
 * list (unlike worker's fixed 2 rows), so the natural shape is full address sub-resource CRUD
 * (list/add/update/delete one address at a time, each re-validating the "exactly one primary" rule),
 * not a single fixed-shape endpoint per address type the way {@code AdminWorkerController} exposes
 * {@code GET/PUT .../address/{type}}.</p>
 */
@Service
public class CompanyService {

    private static final String OWNER_INVITE_TEMPLATE = "COMPANY_OWNER_INVITE";

    /** status_reason is required only for the two "something went wrong" terminal-ish states - mirrors WorkerService's REASON_REQUIRED_STATUSES shape. */
    private static final Set<CompanyStatus> REASON_REQUIRED_STATUSES = EnumSet.of(CompanyStatus.SUSPENDED, CompanyStatus.REJECTED);

    /**
     * Explicit whitelist of allowed transitions - see {@link CompanyStatus}'s Javadoc for the
     * business reasoning behind each one and each deliberate omission (REJECTED-&gt;ACTIVE,
     * ACTIVE-&gt;REJECTED, and every other pair not listed here, are all refused).
     */
    private static final Map<CompanyStatus, Set<CompanyStatus>> ALLOWED_TRANSITIONS = Map.of(
            CompanyStatus.PENDING_VERIFICATION, EnumSet.of(CompanyStatus.REJECTED, CompanyStatus.ACTIVE),
            CompanyStatus.REJECTED, EnumSet.of(CompanyStatus.PENDING_VERIFICATION),
            CompanyStatus.ACTIVE, EnumSet.of(CompanyStatus.SUSPENDED),
            CompanyStatus.SUSPENDED, EnumSet.of(CompanyStatus.ACTIVE)
    );

    /** Real, directly queryable scalar columns only - see {@code SortValidation}'s Javadoc for why. Deliberately excludes every field {@link #list}'s PART 1 filter enrichment added (gstVerified/createdFrom/createdTo/stateId/cityId) - none of them are meant to be sortable columns. */
    private static final Set<String> ALLOWED_SORT = Set.of("name", "companyCode", "status", "createdAt");

    /** Column order for {@link #exportToExcel}. */
    private static final List<String> EXPORT_HEADERS = List.of(
            "Company Code", "Name", "Industry", "Status", "GST Number", "PAN Number", "GST Verified", "Created At");

    private final CompanyRepository companies;
    private final CompanyAddressRepository companyAddresses;
    private final CompanyStatusHistoryRepository statusHistory;
    private final CompanyUserRepository companyUsers;
    private final CompanyAuthTokenRepository authTokens;
    private final CompanyRefreshTokenRepository refreshTokens;
    private final IndustryLookupService industryLookup;
    private final GeographyLookupService geography;
    private final GstVerificationPort gstVerificationPort;
    private final CompanyIdentityNumberHasher identityHasher;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final PermissionChecker permissionChecker;
    private final CompanySecurityProperties securityProperties;
    private final CurrentAdmin currentAdmin;
    private final AdminActorLookupService adminActorLookup;
    private final Clock clock;
    private final String frontendBaseUrl;

    public CompanyService(CompanyRepository companies, CompanyAddressRepository companyAddresses,
                          CompanyStatusHistoryRepository statusHistory, CompanyUserRepository companyUsers,
                          CompanyAuthTokenRepository authTokens, CompanyRefreshTokenRepository refreshTokens,
                          IndustryLookupService industryLookup, GeographyLookupService geography,
                          GstVerificationPort gstVerificationPort, CompanyIdentityNumberHasher identityHasher,
                          PasswordEncoder passwordEncoder, NotificationService notificationService,
                          PermissionChecker permissionChecker, CompanySecurityProperties securityProperties,
                          CurrentAdmin currentAdmin, AdminActorLookupService adminActorLookup, Clock clock,
                          @Value("${atw.frontend.base-url}") String frontendBaseUrl) {
        this.companies = companies;
        this.companyAddresses = companyAddresses;
        this.statusHistory = statusHistory;
        this.companyUsers = companyUsers;
        this.authTokens = authTokens;
        this.refreshTokens = refreshTokens;
        this.industryLookup = industryLookup;
        this.geography = geography;
        this.gstVerificationPort = gstVerificationPort;
        this.identityHasher = identityHasher;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.permissionChecker = permissionChecker;
        this.securityProperties = securityProperties;
        this.currentAdmin = currentAdmin;
        this.adminActorLookup = adminActorLookup;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional(readOnly = true)
    public CompanyView get(UUID id) {
        return toView(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanySummaryView> list(CompanySearchCriteria criteria, Pageable pageable) {
        SortValidation.assertSortable(pageable, ALLOWED_SORT);
        List<Specification<Company>> predicates = buildPredicates(criteria);
        Page<Company> page = companies.findAll(Specifications.allOf(predicates), pageable);
        return PageResponse.from(page.map(CompanyService::toSummaryView));
    }

    private List<Specification<Company>> buildPredicates(CompanySearchCriteria criteria) {
        List<Specification<Company>> predicates = new ArrayList<>();
        if (criteria.status() != null && !criteria.status().isEmpty()) {
            predicates.add(CompanySpecifications.hasStatus(criteria.status()));
        }
        if (criteria.industryId() != null && !criteria.industryId().isEmpty()) {
            predicates.add(CompanySpecifications.hasIndustry(criteria.industryId()));
        }
        if (StringUtils.hasText(criteria.search())) {
            predicates.add(CompanySpecifications.matchesSearch(criteria.search()));
        }
        if (criteria.gstVerified() != null) {
            predicates.add(CompanySpecifications.isGstVerified(criteria.gstVerified()));
        }
        if (criteria.createdFrom() != null || criteria.createdTo() != null) {
            predicates.add(CompanySpecifications.createdBetween(criteria.createdFrom(), criteria.createdTo()));
        }
        if (criteria.stateId() != null && !criteria.stateId().isEmpty()) {
            predicates.add(CompanySpecifications.hasState(criteria.stateId()));
        }
        if (criteria.cityId() != null && !criteria.cityId().isEmpty()) {
            predicates.add(CompanySpecifications.hasCity(criteria.cityId()));
        }
        return predicates;
    }

    /**
     * Excel export for the admin company list. Exports exactly {@code ids} when given and non-empty
     * (unknown ids are silently skipped, same as a plain {@code findAllById} would); otherwise falls
     * back to every company matching {@code criteria} (the exact same filter semantics as {@link #list},
     * via the shared {@link #buildPredicates}, just unpaged) - mirrors {@code WorkerService.exportToExcel}'s
     * exact ids-vs-criteria branching. Writes via the shared {@link ExcelWriter}, not a hand-rolled one.
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel(Set<UUID> ids, CompanySearchCriteria criteria) {
        List<Company> companyList = (ids != null && !ids.isEmpty())
                ? companies.findAllById(ids)
                : findAllMatching(buildPredicates(criteria));
        return toExcel(companyList);
    }

    /**
     * {@link Specifications#allOf} returns {@code null} for an empty predicate list (meaning "no
     * filter at all") - safe to pass into the paged {@code findAll(Specification, Pageable)} overload
     * {@link #list} uses, but the unpaged single-arg {@code findAll(Specification)} overload used by
     * an unfiltered export throws {@code InvalidDataAccessApiUsageException: Specification must not
     * be null} instead of treating it the same way - the exact ambiguous-overload gotcha
     * {@link Specifications}'s own class Javadoc documents, just not yet worked around at this unpaged
     * call site. Call the plain, filter-less {@code findAll()} instead whenever there is nothing to
     * filter on, rather than ever passing a null {@link Specification} into the unpaged overload.
     */
    private List<Company> findAllMatching(List<Specification<Company>> predicates) {
        Specification<Company> spec = Specifications.allOf(predicates);
        return spec == null ? companies.findAll() : companies.findAll(spec);
    }

    /**
     * GST/PAN are masked via the exact same mechanism {@link #toView} already uses
     * ({@link PermissionChecker#has} + {@link PiiMasking#maskTail}, via the shared private {@link #mask}
     * helper) - these are sensitive business identifiers and must never leak unmasked to a caller who
     * can't already see them unmasked elsewhere.
     *
     * <p><strong>Industry-name resolution:</strong> {@link IndustryLookupService} exposes no
     * batch-by-ids lookup (unlike {@link GeographyLookupService#listCitiesByIds}, which
     * {@code WorkerService} uses for its own city/state names) - adding one purely for this export
     * would be a bigger cross-module-contract change than this task warrants, so it is deliberately NOT
     * added here (flagged as an open question instead). Each DISTINCT industry id actually present in
     * {@code companyList} is instead resolved once via {@link IndustryLookupService#get} (a single-id
     * lookup), never per row - there are realistically far fewer distinct industries than companies in
     * any export, so this is not a meaningful N+1 concern the way per-row resolution would be.</p>
     */
    private byte[] toExcel(List<Company> companyList) {
        boolean unmasked = permissionChecker.has("company.view_sensitive");
        Map<UUID, String> industryNameById = new HashMap<>();
        for (Company company : companyList) {
            industryNameById.computeIfAbsent(company.getIndustryId(), id -> industryLookup.get(id).name());
        }

        List<List<Object>> rows = new ArrayList<>();
        for (Company company : companyList) {
            // Arrays.asList (not List.of): GST/PAN may legitimately be null, and List.of(...) throws
            // on a null element - ExcelWriter treats a null cell value as a blank cell (see its Javadoc).
            List<Object> row = Arrays.asList(
                    company.getCompanyCode(),
                    company.getName(),
                    industryNameById.get(company.getIndustryId()),
                    company.getStatus().name(),
                    mask(company.getGstNumber(), unmasked),
                    mask(company.getPanNumber(), unmasked),
                    company.getGstVerifiedAt() != null ? "Yes" : "No",
                    company.getCreatedAt() != null ? company.getCreatedAt().toString() : null
            );
            rows.add(row);
        }
        return ExcelWriter.write("Companies", EXPORT_HEADERS, rows);
    }

    /**
     * Creates the company (status {@code PENDING_VERIFICATION}, GST unverified) and its OWNER
     * account in one transaction, then sends the owner an invite email. GST/PAN uniqueness is
     * pre-checked here for a clean {@link ConflictException} in the common case, but the real
     * guarantee is the DB partial unique index on each hash column (see {@code V37__company.sql}) -
     * same accepted check-then-insert race tolerance as {@code WorkerService.create}'s own mobile-number
     * check and {@code IndustryService}'s own name check, not a new gap introduced here.
     *
     * <p><strong>Deliberate deviation from the admin-staff invite mirror:</strong> the invite email is
     * sent via {@link NotificationService#enqueue} (the async transactional-outbox path), not
     * {@link NotificationService#send} (which {@code AdminStaffService.create} uses) - company
     * creation now bundles company + addresses + owner account + invite token in one transaction, so
     * a transient email-provider hiccup must never roll back all of that just because the email
     * couldn't be sent synchronously.</p>
     */
    @Transactional
    public CompanyView create(CreateCompanyCommand command) {
        if (!industryLookup.exists(command.industryId())) {
            throw new ValidationException("Unknown industry id.");
        }
        if (companyUsers.existsByEmailIgnoreCase(command.ownerEmail())) {
            throw new ConflictException("A company user with this email already exists.");
        }
        String gstHash = hashOrNull(command.gstNumber());
        if (gstHash != null && companies.existsByGstNumberHash(gstHash)) {
            throw new ConflictException("A company with this GST number already exists.");
        }
        String panHash = hashOrNull(command.panNumber());
        if (panHash != null && companies.existsByPanNumberHash(panHash)) {
            throw new ConflictException("A company with this PAN number already exists.");
        }
        List<CompanyAddressInput> addressInputs = command.addresses() == null ? List.of() : command.addresses();
        validateAddresses(addressInputs);

        Company company = new Company(generateCompanyCode(), command.name(), command.industryId(),
                command.gstNumber(), gstHash, command.panNumber(), panHash);
        company = companies.save(company);
        saveAddresses(company.getId(), addressInputs);
        statusHistory.save(new CompanyStatusHistory(company.getId(), null, CompanyStatus.PENDING_VERIFICATION, null,
                currentAdmin.id().orElse(null), currentAdmin.email().orElse(null)));

        // Random, never-disclosed password: the account cannot be used to log in until the invite
        // link is used to set a real one - same "unused password" pattern as AdminStaffService.create.
        String unusedPassword = OpaqueTokens.generate();
        CompanyUser owner = new CompanyUser(company.getId(), CompanyUserRole.OWNER, command.ownerName(),
                command.ownerEmail(), command.ownerPhone(), passwordEncoder.encode(unusedPassword));
        owner = companyUsers.save(owner);

        issueOwnerInvite(company, owner);
        return toView(company);
    }

    @Transactional
    public CompanyView update(UUID id, UpdateCompanyCommand command) {
        Company company = findOrThrow(id);
        if (!industryLookup.exists(command.industryId())) {
            throw new ValidationException("Unknown industry id.");
        }
        company.updateProfile(command.name(), command.industryId());
        companies.save(company);
        return toView(company);
    }

    /**
     * Enforces the exact state machine documented on {@link CompanyStatus}. A reason is required
     * only when moving to SUSPENDED/REJECTED; every transition appends one
     * {@link CompanyStatusHistory} row. See {@link #applyStatusChange} for the cascade this fires on
     * the company's own users.
     */
    @Transactional
    public CompanyView updateStatus(UUID id, CompanyStatus newStatus, String reason) {
        Company company = findOrThrow(id);
        CompanyStatus oldStatus = company.getStatus();
        if (oldStatus == newStatus) {
            throw new ValidationException("Company is already " + newStatus + ".");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of()).contains(newStatus)) {
            throw new ValidationException("Cannot move a company from " + oldStatus + " to " + newStatus + ".");
        }
        if (REASON_REQUIRED_STATUSES.contains(newStatus) && !StringUtils.hasText(reason)) {
            throw new ValidationException("A reason is required when setting status to " + newStatus + ".");
        }
        applyStatusChange(company, oldStatus, newStatus, reason);
        return toView(company);
    }

    /**
     * Bulk counterpart to {@link #updateStatus} - applies the exact same guarded transition to every id
     * independently by calling {@link #updateStatus} itself per id, recording a per-id failure instead
     * of aborting the whole batch. One shared {@code @Transactional} is safe here: every exception
     * {@link #updateStatus} can throw ({@link ResourceNotFoundException}, {@link ValidationException})
     * is a clean, pre-write Java check that fails before any flush is attempted - a status change is
     * always a plain column UPDATE, never something that can fail against a live database constraint at
     * flush time, so one failing id can never leave Hibernate's persistence context rollback-only for
     * the ids still to come. {@link BusinessRuleException} is also caught defensively, matching
     * {@code WorkerService.bulkUpdateStatus}'s identical catch clause, even though no current
     * {@link #updateStatus} rule throws one - a future rule added there (the way Worker's own
     * blacklist-requires-super-admin rule did) must not be able to silently escape this batch loop.
     *
     * <p>Self-invoking {@link #updateStatus} (itself {@code @Transactional}) from within this method,
     * both on the same {@code CompanyService} bean, simply joins the already-open transaction (Spring's
     * default REQUIRED propagation) rather than starting a nested one - exactly the behavior wanted
     * here. Contrast {@link #bulkDelete}, which is just as safe sharing one transaction for the same
     * reason (see its own Javadoc), and {@code SkillService.bulkDelete}, which genuinely needs
     * {@code REQUIRES_NEW} per id because a real hard SQL DELETE there can fail against a live FK
     * constraint at flush time.</p>
     */
    @Transactional
    public BulkActionResult bulkUpdateStatus(Set<UUID> ids, CompanyStatus status, String reason) {
        List<BulkActionFailure> failures = new ArrayList<>();
        int successCount = 0;
        for (UUID id : ids) {
            try {
                updateStatus(id, status, reason);
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
     * Every transition this company's status has ever gone through, newest first - an immutable audit
     * trail (see {@link CompanyStatusHistory}'s Javadoc), mirroring {@code WorkerService.listStatusHistory}'s
     * shape exactly.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanyStatusHistoryView> listStatusHistory(UUID id, Pageable pageable) {
        if (!companies.existsById(id)) {
            throw new ResourceNotFoundException("Company not found");
        }
        Page<CompanyStatusHistory> page = statusHistory.findByCompanyIdOrderByCreatedAtDesc(id, pageable);
        Set<UUID> adminIds = page.getContent().stream()
                .map(CompanyStatusHistory::getChangedByAdminId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> namesById = adminActorLookup.findNames(adminIds);
        return PageResponse.from(page.map(h -> new CompanyStatusHistoryView(h.getId(), h.getFromStatus(), h.getToStatus(),
                h.getReason(), ActorRefs.fromAdminId(h.getChangedByAdminId(), namesById), h.getCreatedAt())));
    }

    private void applyStatusChange(Company company, CompanyStatus oldStatus, CompanyStatus newStatus, String reason) {
        company.changeStatus(newStatus, reason);
        companies.save(company);
        statusHistory.save(new CompanyStatusHistory(company.getId(), oldStatus, newStatus, reason,
                currentAdmin.id().orElse(null), currentAdmin.email().orElse(null)));

        if (newStatus == CompanyStatus.SUSPENDED || newStatus == CompanyStatus.REJECTED) {
            disableUsersAndRevokeSessions(company.getId());
        } else if (newStatus == CompanyStatus.ACTIVE) {
            // Not explicitly spelled out in the original spec (which only mandates the disable
            // direction) - added as the obvious, necessary complement: without this, resuming a
            // SUSPENDED company would leave its owner locked out forever with no path back in.
            // Flagged here and in the implementation report rather than silently assumed either way.
            companyUsers.updateStatusForCompany(company.getId(), CompanyUserStatus.ACTIVE);
        }
    }

    /**
     * <strong>CRITICAL - see {@link Company}'s class Javadoc.</strong> Disables every one of this
     * company's users and revokes every one of their refresh tokens, in the same transaction as the
     * status change that triggered it. Reads only ids (never full entities) before the bulk update,
     * so no managed {@code CompanyUser} in this transaction's persistence context can ever be read
     * back stale afterward.
     */
    private void disableUsersAndRevokeSessions(UUID companyId) {
        List<UUID> userIds = companyUsers.findIdsByCompanyId(companyId);
        companyUsers.updateStatusForCompany(companyId, CompanyUserStatus.DISABLED);
        if (!userIds.isEmpty()) {
            refreshTokens.revokeActiveForUsers(userIds, clock.instant());
        }
    }

    /**
     * Soft-deletes a company and applies the same user-disable cascade as suspending/rejecting one -
     * see {@link Company}'s class Javadoc for why ({@code ON DELETE CASCADE} never fires for a soft
     * delete). Mirrors {@code worker.domain.Worker}/{@code identity.domain.AdminUser}'s soft-delete
     * mechanism exactly ({@code deletedAt} + {@code @SQLRestriction}); every satellite row (addresses,
     * status history, the company's users and their own tokens/OTPs) is left completely untouched,
     * preserving full history.
     *
     * <p>Refused unless the company's current status is something other than ACTIVE - mirrors
     * {@code WorkerService.delete}'s identical guard exactly (confirmed with the developer): the
     * caller must change status first (e.g. to SUSPENDED) via the already-guarded {@link #updateStatus}.
     * A deliberate, independent safety check (a conscious two-step before removing a company
     * altogether) on top of the user-disable cascade above, not a replacement for it.</p>
     */
    @Transactional
    public void delete(UUID id) {
        Company company = findOrThrow(id);
        if (company.getStatus() == CompanyStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot delete a company while ACTIVE. Change its status first (e.g. to SUSPENDED), then delete.");
        }
        company.markDeleted(clock.instant());
        companies.save(company);
        disableUsersAndRevokeSessions(id);
    }

    /**
     * Bulk counterpart to {@link #delete} - applies the exact same guarded soft-delete to every id
     * independently by calling {@link #delete} itself per id, recording a per-id failure instead of
     * aborting the whole batch. One shared {@code @Transactional} is safe here too - unlike
     * {@code SkillService.bulkDelete}, which genuinely needs its own {@code REQUIRES_NEW} transaction
     * per id: {@link Company#markDeleted} only ever produces a plain column UPDATE ({@code deleted_at}),
     * never a real SQL DELETE statement (see {@link Company}'s class Javadoc), so there is no live FK
     * constraint that can fail at flush time and poison Hibernate's persistence context for the ids
     * still to come - the exact failure mode {@code REQUIRES_NEW} exists to isolate against in
     * {@code SkillService.bulkDelete} (a genuine hard delete there). Every exception {@link #delete}
     * can throw ({@link ResourceNotFoundException}, {@link BusinessRuleException}) is a clean, pre-write
     * Java check instead.
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
     * Calls {@link GstVerificationPort#verify} against the company's own stored GST number and, on
     * success, stamps {@code gstVerifiedAt}/{@code gstVerificationRef}. A provider-reported negative
     * result (invalid pattern, not found, not active) surfaces as a {@link BusinessRuleException}
     * with the provider's own reason - nothing is persisted in that case, so the admin can simply
     * retry later once the GST number on file is corrected or the registration becomes active.
     */
    @Transactional
    public CompanyView triggerGstVerification(UUID id) {
        Company company = findOrThrow(id);
        if (!StringUtils.hasText(company.getGstNumber())) {
            throw new ValidationException("This company has no GST number on file to verify.");
        }
        GstVerificationResult result = gstVerificationPort.verify(company.getGstNumber());
        if (!result.verified()) {
            throw new BusinessRuleException(
                    StringUtils.hasText(result.failureReason()) ? result.failureReason() : "GST verification failed.");
        }
        company.markGstVerified(result.providerReference(), clock.instant());
        companies.save(company);
        return toView(company);
    }

    private void validateAddresses(List<CompanyAddressInput> addressInputs) {
        if (addressInputs.isEmpty()) {
            return;
        }
        long primaryCount = addressInputs.stream().filter(CompanyAddressInput::primary).count();
        if (primaryCount != 1) {
            throw new ValidationException("Exactly one address must be marked primary.");
        }
        for (CompanyAddressInput input : addressInputs) {
            if (input.stateId() != null && !geography.stateExists(input.stateId())) {
                throw new ValidationException("Unknown state.");
            }
            if (input.cityId() != null && !geography.cityExists(input.cityId())) {
                throw new ValidationException("Unknown city.");
            }
        }
    }

    private void saveAddresses(UUID companyId, List<CompanyAddressInput> addressInputs) {
        for (CompanyAddressInput input : addressInputs) {
            CompanyAddress address = new CompanyAddress(companyId, input.addressType(), input.primary());
            address.update(input.line1(), input.line2(), input.stateId(), input.cityId(), input.pincode());
            companyAddresses.save(address);
        }
    }

    private void issueOwnerInvite(Company company, CompanyUser owner) {
        String token = OpaqueTokens.generate();
        authTokens.save(new CompanyAuthToken(owner.getId(), OpaqueTokens.sha256Hex(token),
                CompanyAuthTokenType.INVITE, clock.instant().plus(securityProperties.inviteTtl())));
        String inviteUrl = frontendBaseUrl + "/company/accept-invite?token=" + token;
        notificationService.enqueue(new SendEmailCommand(OWNER_INVITE_TEMPLATE, owner.getEmail(), Map.of(
                "name", owner.getName(),
                "companyName", company.getName(),
                "inviteUrl", inviteUrl,
                "hours", securityProperties.inviteTtl().toHours())));
    }

    private String hashOrNull(String value) {
        return StringUtils.hasText(value) ? identityHasher.hash(value) : null;
    }

    /** Server-generated, never user-supplied - same mechanism as Worker.workerCode/Industry.code: a dedicated Postgres sequence, never reused, never a join key. */
    private String generateCompanyCode() {
        return "CMP" + String.format("%07d", companies.nextCompanyCodeSequenceValue());
    }

    private Company findOrThrow(UUID id) {
        return companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    /**
     * Single-record path only (get/create/update/updateStatus/triggerGstVerification) - at most 2
     * extra queries (addresses, users), the same accepted cost tier as
     * {@code WorkerService.addressView}'s own per-GET lookups. Never called per-row from {@link #list}.
     */
    private CompanyView toView(Company company) {
        boolean unmasked = permissionChecker.has("company.view_sensitive");
        List<CompanyAddressView> addressViews = companyAddresses.findByCompanyId(company.getId()).stream()
                .map(CompanyService::toAddressView).toList();
        CompanyOwnerView ownerView = companyUsers.findByCompanyId(company.getId()).stream()
                .filter(u -> u.getRole() == CompanyUserRole.OWNER)
                .findFirst()
                .map(u -> new CompanyOwnerView(u.getId(), u.getName(), u.getEmail()))
                .orElse(null);
        return new CompanyView(company.getId(), company.getCompanyCode(), company.getName(), company.getIndustryId(),
                mask(company.getGstNumber(), unmasked), mask(company.getPanNumber(), unmasked),
                company.getGstVerifiedAt(), company.getGstVerificationRef(), company.getStatus(),
                company.getStatusReason(), ownerView, addressViews, company.getCreatedAt());
    }

    private static CompanySummaryView toSummaryView(Company company) {
        return new CompanySummaryView(company.getId(), company.getCompanyCode(), company.getName(),
                company.getIndustryId(), company.getStatus(), company.getCreatedAt());
    }

    private static String mask(String value, boolean unmasked) {
        return unmasked ? value : PiiMasking.maskTail(value);
    }

    private static CompanyAddressView toAddressView(CompanyAddress address) {
        return new CompanyAddressView(address.getId(), address.getAddressType(), address.isPrimary(),
                address.getAddress().getLine1(), address.getAddress().getLine2(),
                address.getAddress().getStateId(), address.getAddress().getCityId(), address.getAddress().getPincode());
    }
}
