package com.casms.cruisepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.cruisepackage.dto.CruisePackageContracts.*;
import com.casms.cruisepackage.entity.CruisePackageTemplate;
import com.casms.cruisepackage.service.CruisePackageBenefitUsagePolicy;
import com.casms.cruisepackage.service.CruisePackageCabinMappingService;
import com.casms.cruisepackage.service.CruisePackageCabinMappingService.Category;
import com.casms.cruisepackage.service.CruisePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping({"/cruise-package-templates","/api/cruise-package-templates","/fare-package-templates","/api/fare-package-templates"})
@PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','OPERATIONS_MANAGER','CRUISE_MANAGER','BOOKING_AGENT') and hasAnyAuthority('TOUR_PACKAGE_MANAGE','CRUISE_READ','CRUISE_PRICING_APPROVE','BOOKING_READ')")
public class CruisePackageController {
    private final CruisePackageService service;
    private final CruisePackageBenefitUsagePolicy benefitUsage;
    private final CruisePackageCabinMappingService cabinMappings;
    @GetMapping("/ship-options")
    @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')")
    public ApiResponse<List<ShipOption>> shipOptions() {
        return ApiResponse.success(service.templateShips());
    }

    @GetMapping("/ship-options/{shipId}/cabin-types")
    @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')")
    public ApiResponse<List<ShipCabinType>> shipCabinTypes(@PathVariable UUID shipId) {
        return ApiResponse.success(service.templateCabinTypes(shipId));
    }

    @GetMapping("/ship-options/{shipId}/published-templates")
    @PreAuthorize("(hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')) or (hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_CREATE'))")
    public ApiResponse<List<PublishedShipTemplate>> publishedShipTemplates(@PathVariable UUID shipId) {
        return ApiResponse.success(service.publishedShipTemplates(shipId));
    }

    @PostMapping("/with-version")
    @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')")
    public ApiResponse<VersionView> createWithVersion(@RequestBody @Valid CreateTemplateWithVersion input) {
        return ApiResponse.success(service.createWithVersion(input));
    }
    @GetMapping("/logical-categories")
    public ApiResponse<List<Category>> logicalCategories(){return ApiResponse.success(cabinMappings.categories(null));}

    @GetMapping("/benefit-usage-options")
    public ApiResponse<Map<String,Object>> usageOptions() {
        return ApiResponse.success(Map.of("usagePolicies",List.of("UNLIMITED","LIMITED"),"usageUnits",benefitUsage.supportedUnits()));
    }
    @GetMapping public ApiResponse<List<CruisePackageTemplate>> templates() { return ApiResponse.success(service.templates()); }
    @GetMapping("/benefit-resources")
    public ApiResponse<List<ResourceOption>> resources(@RequestParam ResourceType type,@RequestParam(required=false) UUID cruiseId) { return ApiResponse.success(service.resources(type,cruiseId)); }
    @PostMapping @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruisePackageTemplate> create(@RequestBody @Valid CreateTemplate input) { return ApiResponse.success(service.createTemplate(input)); }
    @GetMapping("/{id}/versions") public ApiResponse<List<VersionView>> versions(@PathVariable UUID id) { return ApiResponse.success(service.versions(id)); }
    @PostMapping("/{id}/versions") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')")
    public ApiResponse<VersionView> createVersion(@PathVariable UUID id,@RequestBody @Valid CreateVersion input) { return ApiResponse.success(service.createVersion(id,input)); }
    @PostMapping("/versions/{id}/publish") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE') and hasAuthority('SHIP_READ')")
    public ApiResponse<VersionView> publish(@PathVariable UUID id,@RequestBody @Valid Revision input) { return ApiResponse.success(service.publish(id,input)); }
}
