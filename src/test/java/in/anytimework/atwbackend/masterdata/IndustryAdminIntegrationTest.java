package in.anytimework.atwbackend.masterdata;

import in.anytimework.atwbackend.document.FileStorageTestConfig;
import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

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

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_thenGet_roundTrips() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("Construction " + unique(), "CON" + unique()));

        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.imageUrl()).isNull();
        IndustryDto fetched = industryService.get(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
        assertThat(fetched.code()).isEqualTo(created.code());
    }

    @Test
    void create_withDuplicateName_isRejected() {
        String name = "Logistics " + unique();
        industryService.create(new CreateIndustryRequest(name, "LOG" + unique()));

        assertThatThrownBy(() -> industryService.create(new CreateIndustryRequest(name, "LOG2" + unique())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_withDuplicateCode_isRejected() {
        String code = "MFG" + unique();
        industryService.create(new CreateIndustryRequest("Manufacturing " + unique(), code));

        assertThatThrownBy(() -> industryService.create(new CreateIndustryRequest("Manufacturing Two " + unique(), code)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_renamesAndRecodes() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("IT " + unique(), "IT" + unique()));

        String newName = "IT & Software " + unique();
        String newCode = "ITS" + unique();
        IndustryDto updated = industryService.update(created.id(), new UpdateIndustryRequest(newName, newCode));

        assertThat(updated.name()).isEqualTo(newName);
        assertThat(updated.code()).isEqualTo(newCode);
    }

    @Test
    void updateStatus_deactivatesThenReactivates() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("Healthcare " + unique(), "HC" + unique()));

        IndustryDto deactivated = industryService.updateStatus(created.id(), ActiveStatus.INACTIVE);
        assertThat(deactivated.status()).isEqualTo(ActiveStatus.INACTIVE);

        IndustryDto reactivated = industryService.updateStatus(created.id(), ActiveStatus.ACTIVE);
        assertThat(reactivated.status()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void uploadImage_thenGet_exposesAResolvedUrl() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("Jewellery " + unique(), "JWL" + unique()));

        IndustryDto withImage = industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        assertThat(withImage.imageUrl()).isNotBlank();
        assertThat(industryService.get(created.id()).imageUrl()).isNotBlank();
    }

    @Test
    void uploadImage_withDisallowedContentType_isRejectedAndLeavesExistingStateUnchanged() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("Garments " + unique(), "GAR" + unique()));

        assertThatThrownBy(() -> industryService.uploadImage(created.id(), "doc.pdf", "application/pdf", PNG_BYTES))
                .isInstanceOf(ValidationException.class);

        assertThat(industryService.get(created.id()).imageUrl()).isNull();
    }

    @Test
    void removeImage_clearsTheUrl() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("Event Management " + unique(), "EVT" + unique()));
        industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        IndustryDto cleared = industryService.removeImage(created.id());

        assertThat(cleared.imageUrl()).isNull();
    }

    @Test
    void list_doesNotResolveImageUrls_evenWhenAnImageExists() {
        // Deliberate: resolving every row's image URL in list() would be an extra DB lookup plus a
        // live Azure call per icon-bearing row on every list call. get() still resolves it in full.
        IndustryDto created = industryService.create(new CreateIndustryRequest("Textiles " + unique(), "TXT" + unique()));
        industryService.uploadImage(created.id(), "icon.png", "image/png", PNG_BYTES);

        var listed = industryService.list().stream().filter(i -> i.id().equals(created.id())).findFirst().orElseThrow();

        assertThat(listed.imageUrl()).isNull();
        assertThat(industryService.get(created.id()).imageUrl()).isNotBlank();
    }

    @Test
    void uploadImage_replacingAnExistingOne_stillResolvesToAUrl() {
        IndustryDto created = industryService.create(new CreateIndustryRequest("GEMS " + unique(), "GEM" + unique()));
        industryService.uploadImage(created.id(), "first.png", "image/png", PNG_BYTES);

        IndustryDto replaced = industryService.uploadImage(created.id(), "second.png", "image/png", PNG_BYTES);

        assertThat(replaced.imageUrl()).isNotBlank();
    }
}
