package in.anytimework.atwbackend.worker.api.admin;

import in.anytimework.atwbackend.kyc.application.KycStatus;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.api.BulkIdsRequest;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerDesignationsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.AssignWorkerSkillsRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.BankAccountCheckResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.BulkUpdateWorkerStatusRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CheckBankAccountRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CompleteAadhaarVerificationRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerNoteRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerAddressRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerBankDetailRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerStatusRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerAddressResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerBankDetailResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerExportRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerNoteResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatusHistoryResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerStatutoryDetailResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerSummaryResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.SubmitWorkerEsicRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.SubmitWorkerUanRequest;
import in.anytimework.atwbackend.worker.application.WorkerAddressService;
import in.anytimework.atwbackend.worker.application.WorkerBankDetailService;
import in.anytimework.atwbackend.worker.application.WorkerNoteService;
import in.anytimework.atwbackend.worker.application.WorkerSearchCriteria;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.application.WorkerStatutoryDetailService;
import in.anytimework.atwbackend.worker.domain.AddressType;
import in.anytimework.atwbackend.worker.domain.WorkerStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Worker CRUD, designation/skill assignment, status lifecycle, bulk status/delete, Excel export, and
 * the address/bank-detail/notes sub-resources. Every endpoint requires the matching {@code workers.*}
 * permission; bank-detail reads are additionally masked unless the caller also holds
 * {@code workers.view_bank_details} (see {@code WorkerBankDetailService}). Worker KYC itself lives under
 * {@code kyc.api.admin} - see {@code AdminWorkerKycController} - nested at the same
 * {@code /workers/{id}/kyc} path for API discoverability even though it's a different module's
 * controller.
 */
@Tag(name = "Admin - Workers", description = "Worker CRUD, designation/skill assignment, status "
        + "lifecycle, bulk status/delete, Excel export, and the address/bank-detail/notes sub-resources. "
        + "Every endpoint requires the matching workers.* permission (holder noted per endpoint); "
        + "bank-detail reads are additionally masked unless the caller also holds kyc.view_sensitive. "
        + "Worker KYC itself (Aadhaar/PAN submission, document review) and face-biometric enrollment "
        + "live under their own controllers in the kyc/faceauth modules - this controller only ever "
        + "reads their already-computed, read-only summaries (kycStatus, faceEnrollment).")
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/workers")
public class AdminWorkerController {

    private final WorkerService workerService;
    private final WorkerAddressService addressService;
    private final WorkerBankDetailService bankDetailService;
    private final WorkerStatutoryDetailService statutoryDetailService;
    private final WorkerNoteService noteService;

    public AdminWorkerController(WorkerService workerService, WorkerAddressService addressService,
                                 WorkerBankDetailService bankDetailService,
                                 WorkerStatutoryDetailService statutoryDetailService, WorkerNoteService noteService) {
        this.workerService = workerService;
        this.addressService = addressService;
        this.bankDetailService = bankDetailService;
        this.statutoryDetailService = statutoryDetailService;
        this.noteService = noteService;
    }

    private static final String EX_WORKER_ACTIVE = """
            {
              "success": true,
              "data": {
                "id": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e",
                "workerCode": "WRK0000042",
                "firstName": "Ramesh",
                "middleName": "Kumar",
                "lastName": "Sharma",
                "gender": "MALE",
                "dateOfBirth": "1995-08-15",
                "mobileNumber": "9876543210",
                "personalEmail": "ramesh.kumar@example.com",
                "departmentId": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d",
                "industryId": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c",
                "status": "ACTIVE",
                "statusReason": null,
                "profilePhotoUrl": "https://atwstorage.blob.core.windows.net/documents/worker-profile-photo/c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e.jpg?sv=2025-05-05&se=2026-11-13T10%3A15%3A00Z&sr=b&sp=r&sig=REDACTED",
                "designations": [
                  {"id": "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e", "name": "Mason", "primary": true}
                ],
                "skills": [
                  {"id": "9d0e1f2a-3b4c-4d5e-8f6a-7b8c9d0e1f2a", "name": "Wall Plastering"}
                ],
                "kycStatus": "VERIFIED",
                "faceEnrollment": {
                  "workerId": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e",
                  "enrolled": true,
                  "enrolledAt": "2026-10-20T09:12:00Z",
                  "livenessConfidence": 0.97,
                  "photoUrl": "https://atwstorage.blob.core.windows.net/documents/face-enrollment/c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e.jpg?sv=2025-05-05&se=2026-11-13T10%3A15%3A00Z&sr=b&sp=r&sig=REDACTED"
                },
                "currentAddress": {
                  "line1": "Worker Hostel", "line2": "Sector 18", "city": "Gurugram", "state": "Haryana", "pincode": "122001"
                },
                "permanentAddress": {
                  "line1": "12 Home Street", "line2": null, "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"
                },
                "createdAt": "2026-09-01T06:30:00Z"
              },
              "error": null
            }
            """;

    private static final String EX_WORKER_REGISTERED = """
            {
              "success": true,
              "data": {
                "id": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e",
                "workerCode": "WRK0000042",
                "firstName": "Ramesh",
                "middleName": "Kumar",
                "lastName": "Sharma",
                "gender": "MALE",
                "dateOfBirth": "1995-08-15",
                "mobileNumber": "9876543210",
                "personalEmail": "ramesh.kumar@example.com",
                "departmentId": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d",
                "industryId": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c",
                "status": "REGISTERED",
                "statusReason": null,
                "profilePhotoUrl": null,
                "designations": [
                  {"id": "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e", "name": "Mason", "primary": true}
                ],
                "skills": [],
                "kycStatus": "NOT_STARTED",
                "faceEnrollment": null,
                "currentAddress": null,
                "permanentAddress": null,
                "createdAt": "2026-11-12T10:00:00Z"
              },
              "error": null
            }
            """;

    private static final String EX_WORKER_SUSPENDED = """
            {
              "success": true,
              "data": {
                "id": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e",
                "workerCode": "WRK0000042",
                "firstName": "Ramesh",
                "middleName": "Kumar",
                "lastName": "Sharma",
                "gender": "MALE",
                "dateOfBirth": "1995-08-15",
                "mobileNumber": "9876543210",
                "personalEmail": "ramesh.kumar@example.com",
                "departmentId": "1a2b3c4d-5e6f-4a8b-9c1d-2e3f4a5b6c7d",
                "industryId": "6f5e4d3c-2b1a-4f8e-9d7c-6b5a4f3e2d1c",
                "status": "SUSPENDED",
                "statusReason": "Failed a random KYC re-verification check",
                "profilePhotoUrl": null,
                "designations": [
                  {"id": "7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e", "name": "Mason", "primary": true}
                ],
                "skills": [],
                "kycStatus": "VERIFIED",
                "faceEnrollment": null,
                "currentAddress": null,
                "permanentAddress": null,
                "createdAt": "2026-09-01T06:30:00Z"
              },
              "error": null
            }
            """;

    private static final String EX_WORKER_SUMMARY_PAGE = """
            {
              "success": true,
              "data": {
                "content": [
                  {
                    "id": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e",
                    "workerCode": "WRK0000042",
                    "firstName": "Ramesh",
                    "lastName": "Sharma",
                    "mobileNumber": "9876543210",
                    "primaryDesignationName": "Mason",
                    "status": "ACTIVE",
                    "kycStatus": "VERIFIED",
                    "hasProfilePhoto": true,
                    "faceEnrolledAt": "2026-10-20T09:12:00Z",
                    "currentCity": "Gurugram",
                    "currentState": "Haryana",
                    "permanentCity": "Lucknow",
                    "permanentState": "Uttar Pradesh"
                  },
                  {
                    "id": "d8b2f5e3-0a4c-4b5f-9d6e-2a3b4c5d6e7f",
                    "workerCode": "WRK0000043",
                    "firstName": "Suresh",
                    "lastName": "Patel",
                    "mobileNumber": "9876543211",
                    "primaryDesignationName": null,
                    "status": "REGISTERED",
                    "kycStatus": "NOT_STARTED",
                    "hasProfilePhoto": false,
                    "faceEnrolledAt": null,
                    "currentCity": null,
                    "currentState": null,
                    "permanentCity": null,
                    "permanentState": null
                  }
                ],
                "page": 0,
                "size": 100,
                "totalElements": 2,
                "totalPages": 1,
                "first": true,
                "last": true
              },
              "error": null
            }
            """;

    private static final String EX_STATUS_HISTORY_PAGE = """
            {
              "success": true,
              "data": {
                "content": [
                  {
                    "id": "b5c6d7e8-f9a0-4b2c-9d3e-4f5a6b7c8d9e",
                    "fromStatus": "KYC_PENDING",
                    "toStatus": "ACTIVE",
                    "reason": "KYC verified",
                    "changedBy": "system",
                    "changedAt": "2026-09-02T04:00:00Z"
                  },
                  {
                    "id": "a4b5c6d7-e8f9-4a1b-8c2d-3e4f5a6b7c8d",
                    "fromStatus": null,
                    "toStatus": "REGISTERED",
                    "reason": null,
                    "changedBy": "admin@anytimework.in",
                    "changedAt": "2026-09-01T06:30:00Z"
                  }
                ],
                "page": 0,
                "size": 100,
                "totalElements": 2,
                "totalPages": 1,
                "first": true,
                "last": true
              },
              "error": null
            }
            """;

    private static final String EX_BULK_STATUS_RESULT = """
            {
              "success": true,
              "data": {
                "successCount": 2,
                "failures": [
                  {"id": "00000000-0000-4000-8000-000000000099", "reason": "Not found"}
                ]
              },
              "error": null
            }
            """;

    private static final String EX_BULK_DELETE_RESULT = """
            {
              "success": true,
              "data": {
                "successCount": 1,
                "failures": [
                  {"id": "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e", "reason": "Cannot delete a worker while ACTIVE. Change their status first (e.g. to INACTIVE), then delete."}
                ]
              },
              "error": null
            }
            """;

    private static final String EX_NOTE = """
            {
              "success": true,
              "data": {
                "id": "a4b5c6d7-e8f9-4a1b-8c2d-3e4f5a6b7c8d",
                "note": "Called to confirm availability for the Monday shift.",
                "createdBy": "admin@anytimework.in",
                "createdAt": "2026-11-12T11:00:00Z"
              },
              "error": null
            }
            """;

    private static final String EX_NOTE_PAGE = """
            {
              "success": true,
              "data": {
                "content": [
                  {
                    "id": "a4b5c6d7-e8f9-4a1b-8c2d-3e4f5a6b7c8d",
                    "note": "Called to confirm availability for the Monday shift.",
                    "createdBy": "admin@anytimework.in",
                    "createdAt": "2026-11-12T11:00:00Z"
                  }
                ],
                "page": 0,
                "size": 100,
                "totalElements": 1,
                "totalPages": 1,
                "first": true,
                "last": true
              },
              "error": null
            }
            """;

    private static final String EX_ADDRESS = """
            {
              "success": true,
              "data": {
                "line1": "221B, Civil Lines",
                "line2": "Near City Hospital",
                "stateId": "4b2f8e1a-6c3d-4f9a-9e21-7a5d3c8f10b2",
                "cityId": "9d4e2a7c-1f6b-4c8d-8a3e-2b7f9c4d6e01",
                "pincode": "302001",
                "latitude": 26.9124,
                "longitude": 75.7873,
                "formattedAddress": "221B, Civil Lines, Near City Hospital, Jaipur, Rajasthan 302001"
              },
              "error": null
            }
            """;

    private static final String EX_BANK_DETAIL = """
            {
              "success": true,
              "data": {
                "accountHolderName": "Ramesh Kumar Sharma",
                "bankName": "State Bank of India",
                "accountNumber": "**********5678",
                "ifscCode": "SBIN0001234",
                "branchName": "Jaipur Civil Lines Branch",
                "verified": false,
                "verifiedAt": null
              },
              "error": null
            }
            """;

    private static final String EX_BANK_CHECK = """
            {
              "success": true,
              "data": {
                "exists": true,
                "nameMatched": true,
                "nameAtBank": "Ramesh Kumar Sharma"
              },
              "error": null
            }
            """;

    private static final String EX_ERROR_NOT_FOUND = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "RESOURCE_NOT_FOUND",
                "message": "Worker not found",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "a1b2c3d4e5f60718"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_DESIGNATIONS = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "A worker may hold at most 3 designations.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "b2c3d4e5f6071829"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_SKILL = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Skill 'Electrical Wiring' does not belong to any of the selected designations.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "c3d4e5f607182930"
              }
            }
            """;

    private static final String EX_ERROR_CONFLICT_MOBILE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "CONFLICT",
                "message": "A worker with this mobile number already exists.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "d4e5f60718293a4b"
              }
            }
            """;

    private static final String EX_ERROR_CONFLICT_UAN = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "CONFLICT",
                "message": "This UAN is already registered to another worker.",
                "fieldErrors": null,
                "timestamp": "2026-11-16T10:00:00Z",
                "traceId": "5c6d7e0f18293a4c"
              }
            }
            """;

    private static final String EX_ERROR_CONFLICT_ESIC = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "CONFLICT",
                "message": "This ESIC number is already registered to another worker.",
                "fieldErrors": null,
                "timestamp": "2026-11-16T10:01:00Z",
                "traceId": "6d7e0f18293a4c5d"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_STATUS_RULE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "A reason is required when setting status to SUSPENDED.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "e5f60718293a4b5c"
              }
            }
            """;

    private static final String EX_ERROR_BUSINESS_RULE_BLACKLIST = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "BUSINESS_RULE_VIOLATION",
                "message": "Only a super-admin can blacklist a worker or reverse a blacklist.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "f60718293a4b5c6d"
              }
            }
            """;

    private static final String EX_ERROR_BUSINESS_RULE_DELETE_ACTIVE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "BUSINESS_RULE_VIOLATION",
                "message": "Cannot delete a worker while ACTIVE. Change their status first (e.g. to INACTIVE), then delete.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "0718293a4b5c6d7e"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_EMPTY_IDS = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Request validation failed",
                "fieldErrors": [
                  {"field": "ids", "message": "must not be empty"}
                ],
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "18293a4b5c6d7e0f"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_SORT = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Cannot sort by 'kycStatus'. Allowed fields: createdAt, firstName, lastName, mobileNumber, status, workerCode",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "293a4b5c6d7e0f18"
              }
            }
            """;

    private static final String EX_ERROR_ACCESS_DENIED = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "ACCESS_DENIED",
                "message": "You do not have permission to perform this action",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "3a4b5c6d7e0f1829"
              }
            }
            """;

    private static final String EX_ERROR_CONFLICT_AADHAAR_CONSUMED = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "CONFLICT",
                "message": "This Aadhaar verification has already been used to create a worker.",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "4b5c6d7e0f18293a"
              }
            }
            """;

    private static final String EX_ERROR_VALIDATION_IMAGE = """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "File content does not match declared content type image/png",
                "fieldErrors": null,
                "timestamp": "2026-11-13T10:00:00Z",
                "traceId": "5c6d7e0f18293a4b"
              }
            }
            """;

    @Operation(summary = "List workers", description = """
            **Permission:** `workers.view`.

            Paginated, filterable, sortable list of workers, returned as the lighter `WorkerSummaryResponse` \
            (no signed photo URL, no full `faceEnrollment` object - see Get Worker for those).

            **Filters** - every one is optional and independent; a non-empty filter matches ANY of its \
            own values (an OR within that filter), and every non-empty filter is ANDed together with the \
            others. `kycStatus` is resolved from the separate `kyc` module, not a column on this table - \
            filtering by it costs one extra lookup query. `search` matches first/last name, mobile \
            number, worker code, and email. `registeredFrom`/`registeredTo` filter on the worker's \
            registration (creation) date - there is no separate "registration date" field; it's the \
            same value as `createdAt` below, just filterable as whole UTC calendar days.

            **Pagination/sort:** standard Spring pagination (`page`, `size`, `sort`). Default \
            `page-size`/`max-page-size` are controlled centrally by \
            `spring.data.web.pageable.default-page-size` / `max-page-size` (100 / 500 at the time of \
            writing), not by this endpoint. Default sort is `createdAt,desc` (newest first) when `sort` \
            is omitted. `sort` only accepts real, directly-queryable columns: `firstName`, `lastName`, \
            `workerCode`, `mobileNumber`, `status`, `createdAt` - notably **not** `kycStatus` or any \
            batch-resolved display name (e.g. `primaryDesignationName`), since those aren't columns on \
            this table at all.""")
    @Parameter(name = "status", description = "Filter by one or more lifecycle statuses.")
    @Parameter(name = "departmentId", description = "Filter by one or more department ids (Masterdata module).")
    @Parameter(name = "industryId", description = "Filter by one or more industry ids (Masterdata module).")
    @Parameter(name = "designationId", description = "Filter by one or more designation ids - matches a worker holding ANY of these (primary or not).")
    @Parameter(name = "skillId", description = "Filter by one or more skill ids.")
    @Parameter(name = "kycStatus", description = "Filter by one or more KYC statuses, resolved from the separate kyc module.")
    @Parameter(name = "search", description = "Free-text match against first/last name, mobile number, worker code, and email.", example = "sharma")
    @Parameter(name = "registeredFrom", description = "Registered (created) on or after this date (UTC).", example = "2026-01-01")
    @Parameter(name = "registeredTo", description = "Registered (created) on or before this date (UTC).", example = "2026-12-31")
    @Parameter(name = "stateId", description = "Filter by one or more state ids (Masterdata module) - matches a worker whose PERMANENT or CURRENT address references any of these.")
    @Parameter(name = "cityId", description = "Filter by one or more city ids (Masterdata module) - matches a worker whose PERMANENT or CURRENT address references any of these.")
    @Parameter(name = "sort", description = "Allowed values: firstName, lastName, workerCode, mobileNumber, status, createdAt. Default: createdAt,desc.", example = "createdAt,desc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of workers.",
            content = @Content(schema = @Schema(implementation = WorkerSummaryResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_SUMMARY_PAGE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported `sort` field.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_SORT)))
    @GetMapping
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<PageResponse<WorkerSummaryResponse>> list(
            @RequestParam(required = false) Set<WorkerStatus> status,
            @RequestParam(required = false) Set<UUID> departmentId,
            @RequestParam(required = false) Set<UUID> industryId,
            @RequestParam(required = false) Set<UUID> designationId,
            @RequestParam(required = false) Set<UUID> skillId,
            @RequestParam(required = false) Set<KycStatus> kycStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registeredTo,
            @RequestParam(required = false) Set<UUID> stateId,
            @RequestParam(required = false) Set<UUID> cityId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        WorkerSearchCriteria criteria = new WorkerSearchCriteria(
                status, departmentId, industryId, designationId, skillId, kycStatus, search, registeredFrom, registeredTo,
                stateId, cityId);
        return ApiResponse.ok(workerService.list(criteria, pageable));
    }

    @Operation(summary = "Get a worker", description = """
            **Permission:** `workers.view`.

            Full single-worker detail view, including a short-lived signed profile photo URL and the \
            full `faceEnrollment` object (signed enrollment-photo URL included) when enrolled - both are \
            resolved here only, never on the list endpoint, since this is called once per request rather \
            than once per list row.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The worker.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<WorkerResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(workerService.get(id));
    }

    @Operation(summary = "Create a worker", description = """
            **Permission:** `workers.create`.

            Creates a worker's core profile plus their initial designation(s)/skill(s). The new worker \
            always starts at status `REGISTERED` (unless `aadhaarVerificationId` is given, in which case \
            KYC verification auto-advances status to `ACTIVE` - see `aadhaarVerificationId`'s own \
            description). Address and bank detail are **not** part of this request - use the dedicated \
            sub-resource endpoints below once the worker exists.

            **Validation, checked in this order:**
            1. `mobileNumber` must be unique among active (non-deleted) workers.
            2. `departmentId` must reference an existing department.
            3. `industryId`, when given, must reference an existing industry.
            4. `designationIds` - 1 to 3 ids, every one must exist, and `primaryDesignationId` must be \
            one of them.
            5. `skillIds` - each must belong to one of the selected `designationIds`.
            6. `aadhaarVerificationId`, when given, must reference a verification that is `VERIFIED` and \
            not already consumed; the whole creation fails otherwise (rolled back, no worker is left \
            behind).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Worker created.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_REGISTERED)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "A referenced id doesn't exist, or the designation/skill selection breaks a rule (e.g. more than 3 designations).",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_DESIGNATIONS)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mobile number already in use.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_CONFLICT_MOBILE)))
    @PostMapping
    @PreAuthorize("@authz.has('workers.create')")
    public ApiResponse<WorkerResponse> create(@Valid @RequestBody CreateWorkerRequest request) {
        return ApiResponse.ok(workerService.create(request));
    }

    @Operation(summary = "Update a worker's profile", description = """
            **Permission:** `workers.edit`.

            Replaces the worker's core profile fields (name, gender, DOB, mobile, email, department, \
            industry). Designations/skills are untouched - use the dedicated assignment endpoints below. \
            Same `mobileNumber` uniqueness and `departmentId`/`industryId` existence checks as Create, \
            excluding this worker's own current row.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated worker.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "Another worker already has this mobile number.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_CONFLICT_MOBILE)))
    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkerRequest request) {
        return ApiResponse.ok(workerService.update(id, request));
    }

    /** Named "status", not "toggle": an explicit target status avoids a race between two quick toggle calls. */
    @Operation(summary = "Change a worker's status", description = """
            **Permission:** `workers.toggle_status`.

            An explicit target status, never a blind toggle - two quick calls can't race each other into \
            an unpredictable end state this way.

            **Rules:**
            - `REGISTERED`/`KYC_PENDING` can never be set here - they're reached only automatically.
            - Setting the status the worker is already in is rejected (no-op transitions are explicit \
            errors, not silently accepted).
            - A `reason` is **required** when the target is `SUSPENDED`, `BLACKLISTED`, or `INACTIVE`.
            - `ACTIVE` is reachable here only from `SUSPENDED`/`INACTIVE` - from `REGISTERED`/`KYC_PENDING`, \
            a worker becomes `ACTIVE` only by completing KYC verification (automatic, not via this \
            endpoint).
            - Entering **or** leaving `BLACKLISTED` requires the caller to be a super-admin - \
            un-blacklisting is exactly as high-privilege as blacklisting.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New status applied.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_SUSPENDED)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Target is REGISTERED/KYC_PENDING, equals the current status, or reason is missing for SUSPENDED/BLACKLISTED/INACTIVE.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_STATUS_RULE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "Blacklist/un-blacklist attempted by a non-super-admin, or ACTIVE requested directly from REGISTERED/KYC_PENDING.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_BUSINESS_RULE_BLACKLIST)))
    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('workers.toggle_status')")
    public ApiResponse<WorkerResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateWorkerStatusRequest request) {
        return ApiResponse.ok(workerService.updateStatus(id, request.status(), request.reason()));
    }

    @Operation(summary = "Bulk change worker status", description = """
            **Permission:** `workers.toggle_status`.

            Applies the exact same guarded transition as Change Worker Status (above) to every id \
            independently. Partial success is the normal, expected outcome, not an all-or-nothing \
            failure - one id failing its guard (not found, bad target status, missing reason, blacklist \
            without super-admin, ...) is recorded in `failures[]` and does not roll back the ids that \
            already succeeded.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Always 200 - inspect `data.failures` for any ids that didn't apply.",
            content = @Content(schema = @Schema(implementation = BulkActionResult.class),
                    examples = @ExampleObject(value = EX_BULK_STATUS_RESULT)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` is empty.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_EMPTY_IDS)))
    @PostMapping("/bulk-status")
    @PreAuthorize("@authz.has('workers.toggle_status')")
    public ApiResponse<BulkActionResult> bulkUpdateStatus(@Valid @RequestBody BulkUpdateWorkerStatusRequest request) {
        return ApiResponse.ok(workerService.bulkUpdateStatus(request.ids(), request.status(), request.reason()));
    }

    @Operation(summary = "List a worker's status history", description = """
            **Permission:** `workers.view`.

            Every status transition this worker has ever had, newest first - an immutable audit trail, \
            never edited after creation. The very first row (`fromStatus: null`, `toStatus: REGISTERED`) \
            is recorded at worker creation. `changedBy` is the acting admin's username, or `"system"` for \
            the one transition this controller never makes directly: the automatic REGISTERED/KYC_PENDING \
            to ACTIVE move triggered by KYC verification completing (it runs asynchronously, outside any \
            admin's own request).""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of status transitions.",
            content = @Content(schema = @Schema(implementation = WorkerStatusHistoryResponse.class),
                    examples = @ExampleObject(value = EX_STATUS_HISTORY_PAGE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}/status-history")
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<PageResponse<WorkerStatusHistoryResponse>> statusHistory(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(workerService.listStatusHistory(id, pageable));
    }

    @Operation(summary = "Delete (soft) a worker", description = """
            **Permission:** `workers.delete`.

            Soft-deletes a worker - sets `deletedAt`, never a real SQL DELETE. Every satellite row \
            (address, bank detail, designations, skills, notes, status history, KYC profile, \
            face-biometric enrollment) is left completely untouched, preserving full history for \
            audit/reconciliation; nothing is cascaded away. The worker's mobile number becomes available \
            again for a new worker to register with - a deliberate product decision. There is \
            deliberately no restore/undelete endpoint yet.

            **Refused unless the worker's current status is something other than ACTIVE** - change \
            status first (e.g. to `INACTIVE`) via Change Worker Status, then delete. This is a \
            deliberate, independent safety check, not a side effect of soft-delete.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deleted - `data` is null.",
            content = @Content(examples = @ExampleObject(value = "{\"success\": true, \"data\": null, \"error\": null}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Worker is still ACTIVE.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_BUSINESS_RULE_DELETE_ACTIVE)))
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.has('workers.delete')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        workerService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "Bulk delete (soft) workers", description = """
            **Permission:** `workers.delete`.

            Applies the exact same guarded soft-delete as Delete Worker (above) to every id \
            independently. Partial success is expected - an id that's still ACTIVE, or doesn't exist, is \
            recorded in `failures[]` without rolling back the ids that already succeeded.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Always 200 - inspect `data.failures` for any ids that weren't deleted.",
            content = @Content(schema = @Schema(implementation = BulkActionResult.class),
                    examples = @ExampleObject(value = EX_BULK_DELETE_RESULT)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "`ids` is empty.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_EMPTY_IDS)))
    @PostMapping("/bulk-delete")
    @PreAuthorize("@authz.has('workers.delete')")
    public ApiResponse<BulkActionResult> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return ApiResponse.ok(workerService.bulkDelete(request.ids()));
    }

    /** Streams a raw .xlsx file, not the usual {@link ApiResponse} envelope - an exported file is not JSON. */
    @Operation(summary = "Export workers to Excel", description = """
            **Permission:** `workers.export`.

            **Returns a raw `.xlsx` file download (`Content-Disposition: attachment`), NOT the usual \
            JSON envelope** - this is the only endpoint on this controller that doesn't return \
            `ApiResponse`. Columns, in order: Worker Code, First Name, Last Name, Mobile Number, Status, \
            KYC Status, Designation (primary designation's display name, blank if none), Department, \
            Current City, Current State, Permanent City, Permanent State (blank if that address/field \
            isn't on file), Face Enrolled (`Yes`/`No`).

            Give `ids` for an exact set (unknown ids are silently skipped); otherwise every other field \
            is an optional filter with the **exact same semantics as List Workers above** (a non-empty \
            set matches ANY of its values; every non-empty field is ANDed together). Posting `{}` \
            exports every worker, unfiltered. The whole workbook is built in memory, not streamed \
            row-by-row - fine at today's admin-tool scale, called out here as a known, deliberate \
            limitation.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Excel (.xlsx) file.",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks workers.export.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_ACCESS_DENIED)))
    @PostMapping("/export")
    @PreAuthorize("@authz.has('workers.export')")
    public ResponseEntity<byte[]> export(@RequestBody WorkerExportRequest request) {
        WorkerSearchCriteria criteria = new WorkerSearchCriteria(request.status(), request.departmentId(),
                request.industryId(), request.designationId(), request.skillId(), request.kycStatus(), request.search(),
                request.registeredFrom(), request.registeredTo(), request.stateId(), request.cityId());
        byte[] xlsx = workerService.exportToExcel(request.ids(), criteria);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workers.xlsx\"")
                .body(xlsx);
    }

    @Operation(summary = "Replace a worker's designations", description = """
            **Permission:** `workers.edit`.

            Full replace: the worker holds exactly these designations afterward (1 to 3, every id must \
            exist, exactly one marked primary via `primaryDesignationId`). Any skill the worker currently \
            holds that no longer belongs to one of the new designations is **dropped automatically** in \
            this same call - same behavior legacy enforced. Serialized against a concurrent call for the \
            same worker via a row-level lock.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated worker.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Unknown designation id, more than 3 designations, or primaryDesignationId not in designationIds.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_DESIGNATIONS)))
    @PutMapping("/{id}/designations")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> assignDesignations(@PathVariable UUID id, @Valid @RequestBody AssignWorkerDesignationsRequest request) {
        return ApiResponse.ok(workerService.assignDesignations(id, request));
    }

    @Operation(summary = "Replace a worker's skills", description = """
            **Permission:** `workers.edit`.

            Full replace: the worker holds exactly these skills afterward. Each skill must belong to one \
            of the worker's **current** designations (see Replace Designations above) - a skill \
            belonging to a designation the worker doesn't currently hold is rejected.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated worker.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Unknown skill id, or the skill doesn't belong to any of the worker's current designations.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_SKILL)))
    @PutMapping("/{id}/skills")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> assignSkills(@PathVariable UUID id, @Valid @RequestBody AssignWorkerSkillsRequest request) {
        return ApiResponse.ok(workerService.assignSkills(id, request));
    }

    /** See {@code WorkerService.completeAadhaarVerification}'s Javadoc for when this is used over the create-time path. */
    @Operation(summary = "Complete KYC from an Aadhaar verification", description = """
            **Permission:** `workers.edit`.

            Upgrades an **already-existing** worker's KYC using a successful Aadhaar e-KYC verification - \
            the same mechanism Create Worker uses when `aadhaarVerificationId` is given at creation time, \
            exposed separately for a worker who was onboarded manually (e.g. via the kyc module's \
            `submitAadhaar`) and now wants to move to a live, API-verified KYC record. On success, \
            publishes the same KYC-verified event that auto-advances status to `ACTIVE` when the worker \
            is still REGISTERED/KYC_PENDING.

            **Errors:** the worker id must exist; the verification id must exist, be `VERIFIED` (not \
            still pending or failed - a `422`), and not already consumed by another worker (a `409`, \
            along with the Aadhaar number itself already being registered to a different worker).""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "KYC completed; worker updated.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
            description = "Unknown worker id, or unknown verificationId.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "Verification already consumed, or its Aadhaar number already belongs to another worker.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_CONFLICT_AADHAAR_CONSUMED)))
    @PutMapping("/{id}/aadhaar-verification")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> completeAadhaarVerification(@PathVariable UUID id,
                                                                   @Valid @RequestBody CompleteAadhaarVerificationRequest request) {
        return ApiResponse.ok(workerService.completeAadhaarVerification(id, request.verificationId()));
    }

    @Operation(summary = "Upload a worker's profile photo", description = """
            **Permission:** `workers.edit`.

            Multipart form-data with a single part, field name **`file`**. An identity photo, not a KYC \
            evidence document, so it gets the same small, image-only allowance as Masterdata's icons \
            (contrast with the Document module's larger, PDF-inclusive limits used for KYC scans):

            1. File must be non-empty and **at most 2MB**.
            2. Declared `Content-Type` must be exactly `image/png`, `image/jpeg`, or `image/webp`.
            3. The file's actual leading bytes must match that declared type (magic-byte check) - a \
            mismatch is rejected even if the `Content-Type` header looks fine.

            Uploading a new photo when one already exists **replaces** it (the old blob is deleted). The \
            returned `profilePhotoUrl` is a short-lived (~15 minute) signed URL - re-fetch rather than \
            caching it.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @Parameter(name = "file", description = "Image file (png/jpeg/webp, max 2MB).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Photo uploaded; worker updated.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_ACTIVE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Empty file, file over 2MB, disallowed/missing content type, or content doesn't match the declared type.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_VALIDATION_IMAGE)))
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(workerService.uploadProfilePhoto(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @Operation(summary = "Remove a worker's profile photo", description = """
            **Permission:** `workers.edit`.

            Clears the photo, if any. **Not an error if there was no photo to begin with** - this is a \
            "make sure there's no photo" operation, not a "delete this specific photo" one.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated worker; `profilePhotoUrl` is now null.",
            content = @Content(schema = @Schema(implementation = WorkerResponse.class),
                    examples = @ExampleObject(value = EX_WORKER_REGISTERED)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @DeleteMapping("/{id}/photo")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerResponse> removePhoto(@PathVariable UUID id) {
        return ApiResponse.ok(workerService.removeProfilePhoto(id));
    }

    @Operation(summary = "Get a worker's address", description = """
            **Permission:** `workers.view`.

            Ordinary demographic data, no masking (unlike bank detail/KYC). When no address of the \
            given `type` has been saved yet, every field comes back `null` - this is **not** a 404, \
            since the sub-resource simply doesn't exist yet.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @Parameter(name = "type", description = "PERMANENT (home) or CURRENT (working) address - each is a separate row.", example = "CURRENT")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The address (or all-null if none saved yet).",
            content = @Content(schema = @Schema(implementation = WorkerAddressResponse.class),
                    examples = @ExampleObject(value = EX_ADDRESS)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}/address/{type}")
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<WorkerAddressResponse> getAddress(@PathVariable UUID id, @PathVariable AddressType type) {
        return ApiResponse.ok(addressService.get(id, type));
    }

    @Operation(summary = "Update a worker's address", description = """
            **Permission:** `workers.edit`.

            Full replace of the address row for the given `type` - every field is optional and may be \
            sent progressively over several calls, but a field left out of this request clears to null \
            rather than being left at its previous value (this replaces the whole row, it does not \
            patch individual fields). `stateId`/`cityId`, when given, must reference existing Masterdata \
            geography rows.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @Parameter(name = "type", description = "PERMANENT (home) or CURRENT (working) address - each is a separate row.", example = "CURRENT")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated address.",
            content = @Content(schema = @Schema(implementation = WorkerAddressResponse.class),
                    examples = @ExampleObject(value = EX_ADDRESS)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unknown stateId or cityId.",
            content = @Content(examples = @ExampleObject(value = """
                    {"success": false, "data": null, "error": {"code": "VALIDATION_ERROR", "message": "Unknown state.", "fieldErrors": null, "timestamp": "2026-11-13T10:00:00Z", "traceId": "6d7e0f18293a4b5c"}}""")))
    @PutMapping("/{id}/address/{type}")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerAddressResponse> updateAddress(@PathVariable UUID id, @PathVariable AddressType type,
                                                            @Valid @RequestBody UpdateWorkerAddressRequest request) {
        return ApiResponse.ok(addressService.update(id, type, request));
    }

    @Operation(summary = "Get a worker's bank detail", description = """
            **Permission:** `workers.view`.

            `accountNumber` is **masked** (e.g. `**********5678`) unless the caller also holds \
            `workers.view_bank_details` - a dedicated unmask permission, independent of the kyc \
            module's `kyc.view_sensitive` (which only covers Aadhaar/PAN/UAN/ESIC). When no bank detail \
            has been saved yet, every field comes back null/false - not a 404.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The bank detail (masked unless workers.view_bank_details).",
            content = @Content(schema = @Schema(implementation = WorkerBankDetailResponse.class),
                    examples = @ExampleObject(value = EX_BANK_DETAIL)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<WorkerBankDetailResponse> getBankDetail(@PathVariable UUID id) {
        return ApiResponse.ok(bankDetailService.get(id));
    }

    @Operation(summary = "Update a worker's bank detail", description = """
            **Permission:** `workers.edit`.

            Full replace of the bank detail row - every field is optional, captured progressively. \
            `accountNumber` is stored encrypted at rest and is masked back on every read (including this \
            call's own response) unless the caller holds `workers.view_bank_details`. Saving new details \
            always clears any prior verification - re-run verify after any edit.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated bank detail (masked unless workers.view_bank_details).",
            content = @Content(schema = @Schema(implementation = WorkerBankDetailResponse.class),
                    examples = @ExampleObject(value = EX_BANK_DETAIL)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @PutMapping("/{id}/bank-detail")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerBankDetailResponse> updateBankDetail(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateWorkerBankDetailRequest request) {
        return ApiResponse.ok(bankDetailService.update(id, request));
    }

    @Operation(summary = "Verify a worker's bank detail", description = """
            **Permission:** `workers.edit`.

            Calls the configured bank-verification provider (off by default - see \
            `atw.bank-verification.provider`) with the account number/IFSC already on file, and checks \
            the bank's own name-at-account against the declared account holder name. On success, sets \
            `verified=true`/`verifiedAt` (never settable directly by the client - this is the only way \
            those fields change). Fails with a 422 if the provider says the account doesn't exist, or \
            if the names don't reasonably match (case/whitespace-insensitive) - nothing is marked \
            verified in either case. Fails with a 400 if no bank detail has been saved yet.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verified bank detail (masked unless workers.view_bank_details).",
            content = @Content(schema = @Schema(implementation = WorkerBankDetailResponse.class),
                    examples = @ExampleObject(value = EX_BANK_DETAIL)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @PostMapping("/{id}/bank-detail/verify")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerBankDetailResponse> verifyBankDetail(@PathVariable UUID id) {
        return ApiResponse.ok(bankDetailService.verify(id));
    }

    @Operation(summary = "Check a bank account (no worker required)", description = """
            **Permission:** `workers.edit`.

            Standalone, advisory-only check against the configured bank-verification provider (off by \
            default - see `atw.bank-verification.provider`): confirms an account number + IFSC \
            combination is real, and reports whether the bank's own name-at-account matches the given \
            `accountHolderName` (case/whitespace-insensitive). No worker id is needed - useful during \
            intake, before a worker record exists yet.

            **Nothing is persisted by this call.** It does NOT set `verified`/`verifiedAt` on any \
            worker's bank detail - that only ever happens via Verify Worker Bank Detail (above), once \
            the worker and their bank detail row actually exist. Call that endpoint, not this one, to \
            make a verification authoritative.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Check result (always 200 - a non-existent or mismatched account is a normal result, not an error).",
            content = @Content(schema = @Schema(implementation = BankAccountCheckResponse.class),
                    examples = @ExampleObject(value = EX_BANK_CHECK)))
    @PostMapping("/bank-detail/check")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<BankAccountCheckResponse> checkBankAccount(@Valid @RequestBody CheckBankAccountRequest request) {
        return ApiResponse.ok(bankDetailService.check(request.accountNumber(), request.ifscCode(), request.accountHolderName()));
    }

    @Operation(summary = "Get a worker's statutory detail", description = """
            **Permission:** `workers.view`.

            UAN (EPFO) and ESIC number - both masked (e.g. `********2346`) unless the caller also \
            holds `kyc.view_sensitive` (the same sensitivity gate as the kyc module's Aadhaar/PAN - \
            these are the same risk category). When neither has been saved yet, both fields come back \
            null - not a 404.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The statutory detail (masked unless kyc.view_sensitive).",
            content = @Content(schema = @Schema(implementation = WorkerStatutoryDetailResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}/statutory-detail")
    @PreAuthorize("@authz.has('workers.view')")
    public ApiResponse<WorkerStatutoryDetailResponse> getStatutoryDetail(@PathVariable UUID id) {
        return ApiResponse.ok(statutoryDetailService.get(id));
    }

    @Operation(summary = "Submit a worker's UAN", description = """
            **Permission:** `workers.edit`.

            Records the Universal Account Number (EPFO) as declared - no format validation, only a \
            length cap (UAN's format isn't stable/guessable enough to safely check, matching Aadhaar's \
            own precedent). Rejected with 409 if another worker already has this exact UAN on file.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "UAN recorded.",
            content = @Content(schema = @Schema(implementation = WorkerStatutoryDetailResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "This UAN already belongs to a different worker.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_CONFLICT_UAN)))
    @PutMapping("/{id}/statutory-detail/uan")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerStatutoryDetailResponse> submitUan(@PathVariable UUID id,
                                                                @Valid @RequestBody SubmitWorkerUanRequest request) {
        return ApiResponse.ok(statutoryDetailService.updateUan(id, request.uanNumber()));
    }

    @Operation(summary = "Submit a worker's ESIC number", description = """
            **Permission:** `workers.edit`.

            Records the ESIC number as declared - no format validation, only a length cap (ESIC number \
            formats vary by registration era/scheme, not stable enough to safely guess a single \
            pattern). Rejected with 409 if another worker already has this exact ESIC number on file.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "ESIC number recorded.",
            content = @Content(schema = @Schema(implementation = WorkerStatutoryDetailResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "This ESIC number already belongs to a different worker.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_CONFLICT_ESIC)))
    @PutMapping("/{id}/statutory-detail/esic")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerStatutoryDetailResponse> submitEsic(@PathVariable UUID id,
                                                                 @Valid @RequestBody SubmitWorkerEsicRequest request) {
        return ApiResponse.ok(statutoryDetailService.updateEsic(id, request.esicNumber()));
    }

    @Operation(summary = "List a worker's notes", description = """
            **Permission:** `workers.view_comments` (a separate permission from `workers.view` - a \
            caller can see a worker's profile without seeing admin remarks about them, or vice versa).

            Admin remarks, newest first. There is no edit endpoint for a note, but it can be removed \
            entirely - see Delete Worker Note below.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of notes.",
            content = @Content(schema = @Schema(implementation = WorkerNoteResponse.class),
                    examples = @ExampleObject(value = EX_NOTE_PAGE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @GetMapping("/{id}/notes")
    @PreAuthorize("@authz.has('workers.view_comments')")
    public ApiResponse<PageResponse<WorkerNoteResponse>> listNotes(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.ok(noteService.list(id, pageable));
    }

    @Operation(summary = "Add a note to a worker", description = """
            **Permission:** `workers.edit` (note this is `.edit`, not `.view_comments` - reading notes \
            and adding one are gated by two different permissions).

            Appends one admin remark; there's no edit for it afterward, though it can be deleted - see \
            Delete Worker Note below.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Note created.",
            content = @Content(schema = @Schema(implementation = WorkerNoteResponse.class),
                    examples = @ExampleObject(value = EX_NOTE)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown worker id.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @PostMapping("/{id}/notes")
    @PreAuthorize("@authz.has('workers.edit')")
    public ApiResponse<WorkerNoteResponse> createNote(@PathVariable UUID id, @Valid @RequestBody CreateWorkerNoteRequest request) {
        return ApiResponse.ok(noteService.create(id, request));
    }

    @Operation(summary = "Delete a worker's note", description = """
            **Permission:** `workers.delete_comments` - a third, dedicated permission, separate from \
            both `workers.view_comments` (list) and `workers.edit` (create), since a caller trusted to \
            read or add remarks isn't automatically trusted to remove one someone else wrote.

            **Hard delete** - the note row is permanently removed, with no recovery (unlike `Worker`'s \
            own soft-delete). Returns 404 if the note doesn't exist, or exists but belongs to a \
            different worker than `id` names.""")
    @Parameter(name = "id", description = "Worker id.", example = "c7a1e4d2-9b3f-4a6e-8c5d-1f2a3b4c5d6e")
    @Parameter(name = "noteId", description = "Note id.", example = "a4b5c6d7-e8f9-4a1b-8c2d-3e4f5a6b7c8d")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Deleted - `data` is null.",
            content = @Content(examples = @ExampleObject(value = "{\"success\": true, \"data\": null, \"error\": null}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown note id, or it belongs to a different worker.",
            content = @Content(examples = @ExampleObject(value = EX_ERROR_NOT_FOUND)))
    @DeleteMapping("/{id}/notes/{noteId}")
    @PreAuthorize("@authz.has('workers.delete_comments')")
    public ApiResponse<Void> deleteNote(@PathVariable UUID id, @PathVariable UUID noteId) {
        noteService.delete(id, noteId);
        return ApiResponse.ok();
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
