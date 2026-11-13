package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DesignationResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.application.DepartmentSearchCriteria;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import in.anytimework.atwbackend.worker.api.admin.dto.CreateWorkerRequest;
import in.anytimework.atwbackend.worker.application.WorkerService;
import in.anytimework.atwbackend.worker.domain.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for department CRUD against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
class DepartmentAdminIntegrationTest {

    @Autowired
    private DepartmentService departmentService;
    @Autowired
    private WorkerService workerService;
    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueMobile() {
        return "9" + String.valueOf(System.nanoTime()).substring(0, 9);
    }

    /** A worker assigned to {@code departmentId} - exists purely to exercise the delete-guard tests below. */
    private void createWorkerInDepartment(UUID departmentId) {
        IndustryResponse industry = industryService.create(new CreateIndustryRequest("Ind " + unique()));
        DesignationResponse designation = designationService.create(
                new CreateDesignationRequest("Role " + unique(), Set.of(industry.id()), null));
        workerService.create(new CreateWorkerRequest("Test", null, "Worker " + unique(), Gender.MALE, null,
                uniqueMobile(), null, departmentId, null, Set.of(designation.id()), designation.id(), Set.of()));
    }

    @Test
    void create_thenGet_roundTrips() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest("Operations " + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        DepartmentResponse fetched = departmentService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
    }

    @Test
    void create_withDuplicateName_isRejected() {
        String name = "HR " + unique();
        departmentService.create(new CreateDepartmentRequest(name));

        assertThatThrownBy(() -> departmentService.create(new CreateDepartmentRequest(name)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_renames() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest("Packing " + unique()));

        String newName = "Packing Renamed " + unique();
        DepartmentResponse updated = departmentService.update(created.id(), new UpdateDepartmentRequest(newName));

        assertThat(updated.name()).isEqualTo(newName);
    }

    @Test
    void update_toAnotherDepartmentsName_isRejected() {
        DepartmentResponse first = departmentService.create(new CreateDepartmentRequest("QA " + unique()));
        DepartmentResponse second = departmentService.create(new CreateDepartmentRequest("Production " + unique()));

        assertThatThrownBy(() -> departmentService.update(second.id(), new UpdateDepartmentRequest(first.name())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest("Merchant " + unique()));

        DepartmentResponse deactivated = departmentService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        DepartmentResponse reactivated = departmentService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void list_includesCreatedDepartments() {
        String name = "Sweeper " + unique();
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest(name));

        // Filtered by this test's own unique name, not an unfiltered first page - this test database
        // accumulates rows across every test run in a session, so an unfiltered page 0 can't be
        // assumed to contain a just-created row once enough prior runs have piled up ahead of it.
        List<DepartmentResponse> page = departmentService
                .list(new DepartmentSearchCriteria(null, name), PageRequest.of(0, 100))
                .content();
        assertThat(page).extracting(DepartmentResponse::id).containsExactly(created.id());
    }

    @Test
    void list_filtersByStatusAndSearch() {
        String name = "FilterTarget " + unique();
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest(name));
        departmentService.updateStatus(created.id(), ActiveStatus.INACTIVE);

        List<DepartmentResponse> activeOnly = departmentService
                .list(new DepartmentSearchCriteria(Set.of(ActiveStatus.ACTIVE), null), PageRequest.of(0, 100))
                .content();
        assertThat(activeOnly).extracting(DepartmentResponse::id).doesNotContain(created.id());

        List<DepartmentResponse> bySearch = departmentService
                .list(new DepartmentSearchCriteria(null, name), PageRequest.of(0, 100))
                .content();
        assertThat(bySearch).extracting(DepartmentResponse::id).containsExactly(created.id());
    }

    @Test
    void list_sortsByNameAscendingOrDescending() {
        String prefix = "SortDept" + unique();
        DepartmentResponse beta = departmentService.create(new CreateDepartmentRequest(prefix + " Beta"));
        DepartmentResponse alpha = departmentService.create(new CreateDepartmentRequest(prefix + " Alpha"));

        List<DepartmentResponse> ascending = departmentService
                .list(new DepartmentSearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").ascending()))
                .content();
        assertThat(ascending).extracting(DepartmentResponse::id).containsExactly(alpha.id(), beta.id());

        List<DepartmentResponse> descending = departmentService
                .list(new DepartmentSearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").descending()))
                .content();
        assertThat(descending).extracting(DepartmentResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> departmentService.list(new DepartmentSearchCriteria(null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        DepartmentResponse first = departmentService.create(new CreateDepartmentRequest("Bulk1 " + unique()));
        DepartmentResponse second = departmentService.create(new CreateDepartmentRequest("Bulk2 " + unique()));

        BulkActionResult result = departmentService.bulkUpdateStatus(
                Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(departmentService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(departmentService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        DepartmentResponse real = departmentService.create(new CreateDepartmentRequest("Bulk3 " + unique()));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = departmentService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.id()).containsExactly(bogusId);
        assertThat(departmentService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void delete_removesADepartmentWithNoWorkersAssigned() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest("Deletable " + unique()));

        departmentService.delete(created.id());

        assertThatThrownBy(() -> departmentService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_aDepartmentStillAssignedToAWorker_isRejectedWithAClearMessage() {
        DepartmentResponse created = departmentService.create(new CreateDepartmentRequest("InUse " + unique()));
        createWorkerInDepartment(created.id());

        assertThatThrownBy(() -> departmentService.delete(created.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("workers");

        // Still there afterward - the failed delete must not have left it half-removed.
        assertThat(departmentService.get(created.id())).isNotNull();
    }

    @Test
    void bulkDelete_partialSuccess_deletesTheFreeOneAndReportsTheBlockedOneWithoutLosingData() {
        DepartmentResponse deletable = departmentService.create(new CreateDepartmentRequest("BulkDel1 " + unique()));
        DepartmentResponse blocked = departmentService.create(new CreateDepartmentRequest("BulkDel2 " + unique()));
        createWorkerInDepartment(blocked.id());

        BulkActionResult result = departmentService.bulkDelete(Set.of(deletable.id(), blocked.id()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(blocked.id());

        assertThatThrownBy(() -> departmentService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(departmentService.get(blocked.id())).isNotNull();
    }
}
