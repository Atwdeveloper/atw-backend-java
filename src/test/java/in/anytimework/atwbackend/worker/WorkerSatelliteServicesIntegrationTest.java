package in.anytimework.atwbackend.worker;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.api.PageResponse;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerNoteRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerAddressRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerBankDetailRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerAddressResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerBankDetailResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerNoteResponse;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerResponse;
import in.anytimework.atwbackend.worker.application.WorkerAddressService;
import in.anytimework.atwbackend.worker.application.WorkerBankDetailService;
import in.anytimework.atwbackend.worker.application.WorkerNoteService;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.AddressType;
import in.anytimework.atwbackend.worker.domain.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Address (permanent/current)/bank-detail/notes sub-resources against the real local test Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class WorkerSatelliteServicesIntegrationTest {

    @Autowired
    private WorkerService workerService;
    @Autowired
    private WorkerAddressService addressService;
    @Autowired
    private WorkerBankDetailService bankDetailService;
    @Autowired
    private WorkerNoteService noteService;
    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;
    @Autowired
    private GeographyLookupService geography;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    private UUID createWorker() {
        DepartmentResponse department = departmentService.create(new CreateDepartmentRequest("Dept " + unique()));
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        DesignationResponse designation = designationService.create(
                new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), null));
        WorkerResponse worker = workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(),
                Gender.MALE, null, uniqueMobile(), null, department.id(), null,
                Set.of(designation.id()), designation.id(), Set.of()));
        return worker.id();
    }

    @Test
    void address_getBeforeAnyUpdate_returnsEmptyPlaceholder() {
        UUID workerId = createWorker();
        WorkerAddressResponse address = addressService.get(workerId, AddressType.PERMANENT);
        assertThat(address.line1()).isNull();
    }

    @Test
    void address_permanentAndCurrent_areIndependent() {
        UUID workerId = createWorker();
        UUID stateId = geography.listStates().get(0).id();

        addressService.update(workerId, AddressType.PERMANENT,
                new UpdateWorkerAddressRequest("12 Home St", null, stateId, null, "226001"));
        addressService.update(workerId, AddressType.CURRENT,
                new UpdateWorkerAddressRequest("Worker Hostel", "Sector 18", stateId, null, "122001"));

        WorkerAddressResponse permanent = addressService.get(workerId, AddressType.PERMANENT);
        WorkerAddressResponse current = addressService.get(workerId, AddressType.CURRENT);

        assertThat(permanent.line1()).isEqualTo("12 Home St");
        assertThat(permanent.pincode()).isEqualTo("226001");
        assertThat(current.line1()).isEqualTo("Worker Hostel");
        assertThat(current.pincode()).isEqualTo("122001");
    }

    @Test
    void address_lineIsStoredAsPlainTextNotEncrypted() {
        // Addresses were encrypted at rest until 2026-11-13 (confirmed with the developer to remove
        // it - see Address's Javadoc); this bypasses Hibernate to read the raw stored column and
        // guards against that decision being silently reversed by a future @Convert re-added here.
        UUID workerId = createWorker();
        String plaintext = "12 Home Street, Lucknow";
        addressService.update(workerId, AddressType.PERMANENT,
                new UpdateWorkerAddressRequest(plaintext, null, null, null, null));

        String rawStored = jdbcTemplate.queryForObject(
                "select line1 from worker_address where worker_id = ? and address_type = 'PERMANENT'",
                String.class, workerId);

        assertThat(rawStored).isEqualTo(plaintext);
    }

    @Test
    void address_withUnknownState_isRejected() {
        UUID workerId = createWorker();
        assertThatThrownBy(() -> addressService.update(workerId, AddressType.PERMANENT,
                new UpdateWorkerAddressRequest(null, null, UUID.randomUUID(), null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void bankDetail_update_thenGet_roundTrips() {
        UUID workerId = createWorker();
        WorkerBankDetailResponse updated = bankDetailService.update(workerId,
                new UpdateWorkerBankDetailRequest("Ramesh Kumar", "SBI", "31234567890", "SBIN0001234", "Main Branch"));

        // Without kyc.view_sensitive (no authenticated caller in this test), the account number must be masked.
        assertThat(updated.accountNumber()).isNotEqualTo("31234567890");
        assertThat(updated.accountNumber()).endsWith("7890");
        assertThat(updated.bankName()).isEqualTo("SBI");
    }

    @Test
    void notes_createThenList_mostRecentFirst() {
        UUID workerId = createWorker();
        noteService.create(workerId, new CreateWorkerNoteRequest("First call - no answer"));
        noteService.create(workerId, new CreateWorkerNoteRequest("Second call - confirmed availability"));

        PageResponse<WorkerNoteResponse> notes = noteService.list(workerId, PageRequest.of(0, 10));

        assertThat(notes.content()).hasSize(2);
        assertThat(notes.content().get(0).note()).isEqualTo("Second call - confirmed availability");
    }
}
