package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateIndustryRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.IndustryDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateIndustryRequest;
import in.anytimework.atwbackend.masterdata.application.IndustryService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import java.util.List;
import java.util.UUID;

/** Industry CRUD + icon upload. Every endpoint requires the matching {@code industry.*} permission. */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/industries")
public class IndustryController {

    private final IndustryService industryService;

    public IndustryController(IndustryService industryService) {
        this.industryService = industryService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('industry.view')")
    public ApiResponse<List<IndustryDto>> list() {
        return ApiResponse.ok(industryService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('industry.view')")
    public ApiResponse<IndustryDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(industryService.get(id));
    }

    @PostMapping
    @PreAuthorize("@authz.has('industry.create')")
    public ApiResponse<IndustryDto> create(@Valid @RequestBody CreateIndustryRequest request) {
        return ApiResponse.ok(industryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.has('industry.edit')")
    public ApiResponse<IndustryDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateIndustryRequest request) {
        return ApiResponse.ok(industryService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('industry.toggle_status')")
    public ApiResponse<IndustryDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(industryService.updateStatus(id, request.status()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.has('industry.edit')")
    public ApiResponse<IndustryDto> uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(industryService.uploadImage(id, file.getOriginalFilename(), file.getContentType(), readBytes(file)));
    }

    @DeleteMapping("/{id}/image")
    @PreAuthorize("@authz.has('industry.edit')")
    public ApiResponse<IndustryDto> removeImage(@PathVariable UUID id) {
        return ApiResponse.ok(industryService.removeImage(id));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read uploaded file");
        }
    }
}
