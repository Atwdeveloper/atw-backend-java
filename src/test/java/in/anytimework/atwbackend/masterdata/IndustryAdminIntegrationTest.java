package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateDesignationRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryResponse;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.application.DesignationService;
import in.anytimework.atwbackend.masterdata.application.IndustrySearchCriteria;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.BulkActionResult;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end coverage for industry CRUD + icon upload against the real local Postgres. */
@SpringBootTest
@ActiveProfiles("test")
@Import(FileStorageTestConfig.class)
class IndustryAdminIntegrationTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};

    @Autowired
    private IndustryService industryService;
    @Autowired
    private DesignationService designationService;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_thenGet_roundTrips() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Construction " + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.imageUrl()).isNull();
        IndustryResponse fetched = industryService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
        assertThat(fetched.code()).isEqualTo(created.code());
    }

    @Test
    void create_generatesAUniqueCodeServerSide() {
        IndustryResponse first = industryService.create(new CreateIndustryRequest("CodeGenA " + unique()));
        IndustryResponse second = industryService.create(new CreateIndustryRequest("CodeGenB " + unique()));

        assertThat(first.code()).startsWith("IND");
        assertThat(second.code()).startsWith("IND");
        assertThat(first.code()).isNotEqualTo(second.code());
    }

    @Test
    void create_withDuplicateName_isRejected() {
        String name = "Logistics " + unique();
        industryService.create(new CreateIndustryRequest(name));

        assertThatThrownBy(() -> industryService.create(new CreateIndustryRequest(name)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_renames() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("IT " + unique()));

        String newName = "IT & Software " + unique();
        IndustryResponse updated = industryService.update(created.id(), new UpdateIndustryRequest(newName));

        assertThat(updated.name()).isEqualTo(newName);
        // code is assigned once at creation and never changes, even across an unrelated rename.
        assertThat(updated.code()).isEqualTo(created.code());
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Healthcare " + unique()));

        IndustryResponse deactivated = industryService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        IndustryResponse reactivated = industryService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Jewellery " + unique()));

        IndustryResponse withImage = industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
        assertThat(industryService.get(created.id()).imageUrl()).isNotBlank();
    }

    @Test
    void uploadImage_withDisallowedContentType_isRejectedAndLeavesExistingStateUnchanged() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Garments " + unique()));

        assertThatThrownBy(() -> industryService.uploadImage(created.id(), "doc.pdf", "application/pdf", PNG_BYTES))
                .isInstanceOf(ValidationException.class);

        assertThat(industryService.get(created.id()).imageUrl()).isNull();
    }

    @Test
    void removeImage_clearsTheUrl() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Event Management " + unique()));
        industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        IndustryResponse cleared = industryService.removeImage(created.id());

        assertThat(cleared.imageUrl()).isNull();
    }

    @Test
    void uploadImage_replacingAnExistingOne_stillResolvesToAUrl() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("GEMS " + unique()));
        industryService.uploadImage(created.id(), "first.png", "image/png", PNG_BYTES);

        IndustryResponse replaced = industryService.uploadImage(created.id(), "second.png", "image/png", PNG_BYTES);

        assertThat(replaced.imageUrl()).isNotBlank();
    }

    @Test
    void list_filtersByMultipleStatuses_orWithinFieldAndAndAcrossFields() {
        String sharedTerm = "MultiStatus " + unique();
        IndustryResponse active = industryService.create(new CreateIndustryRequest(sharedTerm + " Active"));
        IndustryResponse toDeactivate = industryService.create(new CreateIndustryRequest(sharedTerm + " Inactive"));
        IndustryResponse inactive = industryService.updateStatus(toDeactivate.id(), ActiveStatus.INACTIVE);

        // OR within the statuses field: both ACTIVE and INACTIVE rows come back together.
        List<IndustryResponse> both = industryService
                .list(new IndustrySearchCriteria(Set.of(ActiveStatus.ACTIVE, ActiveStatus.INACTIVE), sharedTerm), PageRequest.of(0, 100))
                .content();
        assertThat(both).extracting(IndustryResponse::id).containsExactlyInAnyOrder(active.id(), inactive.id());

        // AND across fields: narrowing statuses to ACTIVE only (same search term) excludes the inactive one.
        List<IndustryResponse> activeOnly = industryService
                .list(new IndustrySearchCriteria(Set.of(ActiveStatus.ACTIVE), sharedTerm), PageRequest.of(0, 100))
                .content();
        assertThat(activeOnly).extracting(IndustryResponse::id).containsExactly(active.id());
    }

    @Test
    void list_resolvesAnImageUrlForARowThatHasOne() {
        String name = "Mining " + unique();
        IndustryResponse created = industryService.create(new CreateIndustryRequest(name));
        industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        // Filtered by this test's own full unique name - the test database accumulates rows across
        // every run in this session, so an unfiltered first page can't be assumed to contain it.
        List<IndustryResponse> page = industryService
                .list(new IndustrySearchCriteria(null, name), PageRequest.of(0, 100))
                .content();

        assertThat(page).hasSize(1);
        assertThat(page.get(0).imageUrl()).isNotBlank();
    }

    @Test
    void list_sortsByNameAscendingOrDescending() {
        String prefix = "SortInd" + unique();
        IndustryResponse beta = industryService.create(new CreateIndustryRequest(prefix + " Beta"));
        IndustryResponse alpha = industryService.create(new CreateIndustryRequest(prefix + " Alpha"));

        List<IndustryResponse> ascending = industryService
                .list(new IndustrySearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").ascending()))
                .content();
        assertThat(ascending).extracting(IndustryResponse::id).containsExactly(alpha.id(), beta.id());

        List<IndustryResponse> descending = industryService
                .list(new IndustrySearchCriteria(null, prefix), PageRequest.of(0, 100, Sort.by("name").descending()))
                .content();
        assertThat(descending).extracting(IndustryResponse::id).containsExactly(beta.id(), alpha.id());
    }

    @Test
    void list_rejectsAnUnsortableField() {
        assertThatThrownBy(() -> industryService.list(new IndustrySearchCriteria(null, null),
                PageRequest.of(0, 10, Sort.by("someUnknownField"))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("someUnknownField");
    }

    @Test
    void bulkUpdateStatus_updatesEveryIdAndReportsSuccessCount() {
        IndustryResponse first = industryService.create(new CreateIndustryRequest("Bulk1 " + unique()));
        IndustryResponse second = industryService.create(new CreateIndustryRequest("Bulk2 " + unique()));

        BulkActionResult result = industryService.bulkUpdateStatus(
                Set.of(first.id(), second.id()), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(industryService.get(first.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(industryService.get(second.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void bulkUpdateStatus_withAnUnknownId_reportsItAsAFailureWithoutAffectingOthers() {
        IndustryResponse real = industryService.create(new CreateIndustryRequest("Bulk3 " + unique()));
        UUID bogusId = UUID.randomUUID();

        BulkActionResult result = industryService.bulkUpdateStatus(Set.of(real.id(), bogusId), ActiveStatus.INACTIVE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).extracting(f -> f.id()).containsExactly(bogusId);
        assertThat(industryService.get(real.id()).status()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void delete_removesAnIndustryWithNoReferences() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("Deletable " + unique()));

        industryService.delete(created.id());

        assertThatThrownBy(() -> industryService.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_anIndustryStillAssignedToADesignation_isRejectedWithAClearMessage() {
        IndustryResponse created = industryService.create(new CreateIndustryRequest("InUse " + unique()));
        designationService.create(new CreateDesignationRequest("Role " + unique(), Set.of(created.id()), null));

        assertThatThrownBy(() -> industryService.delete(created.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assigned");

        // Still there afterward - the failed delete must not have left it half-removed.
        assertThat(industryService.get(created.id())).isNotNull();
    }

    @Test
    void bulkDelete_partialSuccess_deletesTheFreeOneAndReportsTheBlockedOneWithoutLosingData() {
        IndustryResponse deletable = industryService.create(new CreateIndustryRequest("BulkDel1 " + unique()));
        IndustryResponse blocked = industryService.create(new CreateIndustryRequest("BulkDel2 " + unique()));
        designationService.create(new CreateDesignationRequest("Role " + unique(), Set.of(blocked.id()), null));

        BulkActionResult result = industryService.bulkDelete(Set.of(deletable.id(), blocked.id()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(blocked.id());

        assertThatThrownBy(() -> industryService.get(deletable.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(industryService.get(blocked.id())).isNotNull();
    }
}
