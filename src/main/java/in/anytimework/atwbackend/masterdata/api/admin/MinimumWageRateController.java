package in.anytimework.atwbackend.masterdata.api.admin;

import in.anytimework.atwbackend.masterdata.api.admin.dto.CreateMinimumWageRateRequest;
import in.anytimework.atwbackend.masterdata.api.admin.dto.MinimumWageRateDto;
import in.anytimework.atwbackend.masterdata.api.admin.dto.UpdateActiveStatusRequest;
import in.anytimework.atwbackend.masterdata.application.MinimumWageRateService;
import in.anytimework.atwbackend.shared.api.ApiResponse;
import in.anytimework.atwbackend.shared.api.ApiVersions;
import in.anytimework.atwbackend.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimum-wage rate CRUD. No update/edit endpoint for the rate value itself - see
 * {@code MinimumWageRate}'s Javadoc; a rate change is always a new {@code create} with a new
 * {@code effectiveFrom}. Every endpoint requires the matching {@code minimum_wage.*} permission.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/admin/masterdata/minimum-wage-rates")
public class MinimumWageRateController {

    private final MinimumWageRateService minimumWageRateService;

    public MinimumWageRateController(MinimumWageRateService minimumWageRateService) {
        this.minimumWageRateService = minimumWageRateService;
    }

    @GetMapping
    @PreAuthorize("@authz.has('minimum_wage.view')")
    public ApiResponse<PageResponse<MinimumWageRateDto>> list(
            @RequestParam(required = false) UUID stateId,
            @RequestParam(required = false) UUID skillCategoryId,
            @PageableDefault(sort = "effectiveFrom", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(minimumWageRateService.list(stateId, skillCategoryId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.has('minimum_wage.view')")
    public ApiResponse<MinimumWageRateDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(minimumWageRateService.get(id));
    }

    /** The rate actually in force for a (state, skill category) pair as of a date (defaults to today). */
    @GetMapping("/effective")
    @PreAuthorize("@authz.has('minimum_wage.view')")
    public ApiResponse<MinimumWageRateDto> effective(@RequestParam UUID stateId, @RequestParam UUID skillCategoryId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(minimumWageRateService.resolveEffective(stateId, skillCategoryId, asOf));
    }

    @PostMapping
    @PreAuthorize("@authz.has('minimum_wage.create')")
    public ApiResponse<MinimumWageRateDto> create(@Valid @RequestBody CreateMinimumWageRateRequest request) {
        return ApiResponse.ok(minimumWageRateService.create(request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@authz.has('minimum_wage.toggle_status')")
    public ApiResponse<MinimumWageRateDto> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateActiveStatusRequest request) {
        return ApiResponse.ok(minimumWageRateService.updateStatus(id, request.status()));
    }
}
