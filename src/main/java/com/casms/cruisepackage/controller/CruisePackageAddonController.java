package com.casms.cruisepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.cruisepackage.dto.CruisePackageContracts;
import com.casms.cruisepackage.entity.CruisePackageAddonOffer;
import com.casms.cruisepackage.service.CruisePackageAddonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/cruise/{cruiseId}/cruise-package-addons")
@PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','OPERATIONS_MANAGER','CRUISE_MANAGER','BOOKING_AGENT') and hasAnyAuthority('TOUR_PACKAGE_MANAGE','CRUISE_READ')")
public class CruisePackageAddonController {
    private final CruisePackageAddonService service;
    @GetMapping public ApiResponse<List<CruisePackageAddonOffer>> list(@PathVariable UUID cruiseId){return ApiResponse.success(service.list(cruiseId));}
    @GetMapping("/configuration")
    @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruisePackageAddonService.Configuration> configuration(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.configuration(cruiseId));
    }
    @PostMapping @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruisePackageAddonOffer> create(@PathVariable UUID cruiseId,@RequestBody @Valid CruisePackageAddonService.Input input){return ApiResponse.success(service.create(cruiseId,input));}
    @PostMapping("/{offerId}/publish") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruisePackageAddonOffer> publish(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@RequestBody @Valid CruisePackageContracts.Revision input){return ApiResponse.success(service.publish(cruiseId,offerId,input.expectedRevision()));}
}
