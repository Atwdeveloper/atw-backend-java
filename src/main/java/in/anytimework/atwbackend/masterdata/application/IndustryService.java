package in.anytimework.atwbackend.masterdata.application;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.domain.Industry;
import in.anytimework.atwbackend.masterdata.domain.IndustryRepository;
import in.anytimework.atwbackend.shared.exception.ConflictException;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.kernel.ActiveStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for industries. Authorization ({@code industry.view/create/edit/toggle_status}) is enforced
 * at the controller via {@code @PreAuthorize}, matching every other admin-facing service in this
 * codebase - this service assumes its caller is already authorized.
 */
@Service
public class IndustryService {

    private static final String IMAGE_CATEGORY = "INDUSTRY_ICON";

    private final IndustryRepository industries;
    private final MasterDataImageSupport images;

    IndustryService(IndustryRepository industries, MasterDataImageSupport images) {
        this.industries = industries;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<IndustryDto> list() {
        // toListDto, not toDto: resolving an image URL per row here would be one extra DB lookup, plus
        // a live Azure call under managed-identity SAS signing, FOR EVERY row with an icon - fine for
        // a single get(), not for an unbounded list(). See MasterDataImageSupport.urlOrNull.
        return industries.findAllByOrderByName().stream().map(IndustryService::toListDto).toList();
    }

    @Transactional(readOnly = true)
    public IndustryDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public IndustryDto create(CreateIndustryRequest request) {
        if (industries.existsByName(request.name())) {
            throw new ConflictException("An industry named '" + request.name() + "' already exists.");
        }
        if (industries.existsByCode(request.code())) {
            throw new ConflictException("An industry with code '" + request.code() + "' already exists.");
        }
        Industry industry = new Industry(request.name(), request.code());
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryDto update(UUID id, UpdateIndustryRequest request) {
        Industry industry = findOrThrow(id);
        if (industries.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("An industry named '" + request.name() + "' already exists.");
        }
        if (industries.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("An industry with code '" + request.code() + "' already exists.");
        }
        industry.rename(request.name(), request.code());
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryDto updateStatus(UUID id, ActiveStatus status) {
        Industry industry = findOrThrow(id);
        if (status == ActiveStatus.ACTIVE) {
            industry.activate();
        } else {
            industry.deactivate();
        }
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryDto uploadImage(UUID id, String filename, String contentType, byte[] content) {
        Industry industry = findOrThrow(id);
        UUID newImageId = images.replace(IMAGE_CATEGORY, id, industry.getImageDocumentId(), filename, contentType, content);
        industry.setImageDocumentId(newImageId);
        return toDto(industries.save(industry));
    }

    @Transactional
    public IndustryDto removeImage(UUID id) {
        Industry industry = findOrThrow(id);
        images.remove(industry.getImageDocumentId());
        industry.setImageDocumentId(null);
        return toDto(industries.save(industry));
    }

    private Industry findOrThrow(UUID id) {
        return industries.findById(id).orElseThrow(() -> new ResourceNotFoundException("Industry not found"));
    }

    private IndustryDto toDto(Industry industry) {
        return new IndustryDto(industry.getId(), industry.getName(), industry.getCode(), industry.getStatus(),
                images.urlOrNull(industry.getImageDocumentId()));
    }

    /** Same as {@link #toDto}, minus the per-row image URL resolution - see {@link #list()}. */
    private static IndustryDto toListDto(Industry industry) {
        return new IndustryDto(industry.getId(), industry.getName(), industry.getCode(), industry.getStatus(), null);
    }
}
