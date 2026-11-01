package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.DepartmentDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateDepartmentRequest;
import in.anytimework.atwbackend.masterdata.application.DepartmentService;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for department CRUD against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
class DepartmentAdminIntegrationTest {

    @Autowired
    private DepartmentService departmentService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_thenGet_roundTrips() {
        DepartmentDto created = departmentService.create(new CreateDepartmentRequest("Operations " + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        DepartmentDto fetched = departmentService.get(created.id());
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
        DepartmentDto created = departmentService.create(new CreateDepartmentRequest("Packing " + unique()));

        String newName = "Packing Renamed " + unique();
        DepartmentDto updated = departmentService.update(created.id(), new UpdateDepartmentRequest(newName));

        assertThat(updated.name()).isEqualTo(newName);
    }

    @Test
    void update_toAnotherDepartmentsName_isRejected() {
        DepartmentDto first = departmentService.create(new CreateDepartmentRequest("QA " + unique()));
        DepartmentDto second = departmentService.create(new CreateDepartmentRequest("Production " + unique()));

        assertThatThrownBy(() -> departmentService.update(second.id(), new UpdateDepartmentRequest(first.name())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        DepartmentDto created = departmentService.create(new CreateDepartmentRequest("Merchant " + unique()));

        DepartmentDto deactivated = departmentService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        DepartmentDto reactivated = departmentService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void list_includesCreatedDepartments() {
        DepartmentDto created = departmentService.create(new CreateDepartmentRequest("Sweeper " + unique()));

        assertThat(departmentService.list()).extracting(DepartmentDto::id).contains(created.id());
    }
}
