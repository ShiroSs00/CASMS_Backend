package com.casms.cruisepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.cruisepackage.service.CruisePackageCabinMappingService;
import com.casms.security.principal.CasmsPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping({"/ships/{shipId}/cruise-package-cabin-mapping","/api/ships/{shipId}/cruise-package-cabin-mapping"})
public class CruisePackageCabinMappingController {
    private final CruisePackageCabinMappingService service;
    @GetMapping @PreAuthorize("hasRole('SHIP_MANAGER') and hasAuthority('SHIP_READ')")
    public ApiResponse<CruisePackageCabinMappingService.View> view(@PathVariable UUID shipId){return ApiResponse.success(service.view(shipId));}
    @PostMapping("/confirm") @PreAuthorize("hasRole('SHIP_MANAGER') and hasAuthority('SHIP_CABIN_CATEGORY_MAPPING_CONFIRM')")
    public ApiResponse<CruisePackageCabinMappingService.View> confirm(@PathVariable UUID shipId,@RequestBody @Valid CruisePackageCabinMappingService.Confirmation input,@AuthenticationPrincipal CasmsPrincipal actor){return ApiResponse.success(service.confirm(shipId,input,actor));}
}
