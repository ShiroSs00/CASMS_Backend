package com.casms.cruisepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.cruisepackage.dto.CruisePackageContracts.*;
import com.casms.cruisepackage.entity.CruisePackageTemplate;
import com.casms.cruisepackage.service.CruisePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping({"/cruise-package-templates","/api/cruise-package-templates","/fare-package-templates","/api/fare-package-templates"})
@PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','OPERATIONS_MANAGER','CRUISE_MANAGER','BOOKING_AGENT') and hasAnyAuthority('TOUR_PACKAGE_MANAGE','CRUISE_READ','CRUISE_PRICING_APPROVE','BOOKING_READ')")
public class CruisePackageController {
    private final CruisePackageService service;
    private final com.casms.cruisepackage.service.CruisePackageBenefitUsagePolicy benefitUsage;
    private final com.casms.cruisepackage.service.CruisePackageCabinMappingService cabinMappings;
    @GetMapping("/logical-categories")
    public ApiResponse<List<com.casms.cruisepackage.service.CruisePackageCabinMappingService.Category>> logicalCategories(){return ApiResponse.success(cabinMappings.categories(null));}

    @GetMapping("/benefit-usage-options")
    public ApiResponse<java.util.Map<String,Object>> usageOptions() {
        return ApiResponse.success(java.util.Map.of("usagePolicies",List.of("UNLIMITED","LIMITED"),"usageUnits",benefitUsage.supportedUnits()));
    }
    @GetMapping public ApiResponse<List<CruisePackageTemplate>> templates() { return ApiResponse.success(service.templates()); }
    @GetMapping("/benefit-resources")
    public ApiResponse<List<ResourceOption>> resources(@RequestParam ResourceType type,@RequestParam(required=false) UUID cruiseId) { return ApiResponse.success(service.resources(type,cruiseId)); }
    @PostMapping @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruisePackageTemplate> create(@RequestBody @Valid CreateTemplate input) { return ApiResponse.success(service.createTemplate(input)); }
    @GetMapping("/{id}/versions") public ApiResponse<List<VersionView>> versions(@PathVariable UUID id) { return ApiResponse.success(service.versions(id)); }
    @PostMapping("/{id}/versions") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<VersionView> createVersion(@PathVariable UUID id,@RequestBody @Valid CreateVersion input) { return ApiResponse.success(service.createVersion(id,input)); }
    @PostMapping("/versions/{id}/publish") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<VersionView> publish(@PathVariable UUID id,@RequestBody @Valid Revision input) { return ApiResponse.success(service.publish(id,input)); }
}
