package com.casms.cruisepackage.controller;

import com.casms.common.response.ApiResponse;
import com.casms.cruisepackage.dto.CruisePackageContracts.*;
import com.casms.cruisepackage.service.CruisePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping({"/cruise/{cruiseId}/cruise-package-offers","/api/cruise/{cruiseId}/cruise-package-offers","/cruise/{cruiseId}/fare-package-offers","/api/cruise/{cruiseId}/fare-package-offers"})
@PreAuthorize("hasAnyRole('ACTIVITY_SERVICE_MANAGER','OPERATIONS_MANAGER','CRUISE_MANAGER','BOOKING_AGENT') and hasAnyAuthority('TOUR_PACKAGE_MANAGE','CRUISE_READ','CRUISE_PRICING_APPROVE','BOOKING_READ')")
public class DepartureCruisePackageController {
    private final CruisePackageService service;
    @GetMapping public ApiResponse<List<OfferView>> offers(@PathVariable UUID cruiseId) { return ApiResponse.success(service.offers(cruiseId)); }
    @GetMapping("/available") @PreAuthorize("permitAll()")
    public ApiResponse<CruisePackageCatalog> available(@PathVariable UUID cruiseId) { return ApiResponse.success(service.available(cruiseId)); }
    @GetMapping("/context") public ApiResponse<CruisePackageContext> context(@PathVariable UUID cruiseId) { return ApiResponse.success(service.context(cruiseId)); }
    @GetMapping("/cabin-options") public ApiResponse<List<String>> cabinOptions(@PathVariable UUID cruiseId) { return ApiResponse.success(service.cabinOptions(cruiseId)); }
    @PostMapping @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> create(@PathVariable UUID cruiseId,@RequestBody @Valid OfferInput input) { return ApiResponse.success(service.createOffer(cruiseId,input)); }
    @PostMapping("/{offerId}/upgrade-version") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> upgrade(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@RequestBody @Valid UpgradeOffer input) { return ApiResponse.success(service.upgradeOffer(cruiseId,offerId,input)); }
    @PostMapping("/{offerId}/publish") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> publish(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@RequestBody @Valid Revision input) { return ApiResponse.success(service.publishOffer(cruiseId,offerId,input)); }
    @PostMapping("/{offerId}/prices") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> price(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@RequestBody @Valid CreatePrice input) { return ApiResponse.success(service.createPrice(cruiseId,offerId,input)); }
    @PostMapping("/{offerId}/benefit-bindings") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> bind(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@RequestBody @Valid BindBenefit input) { return ApiResponse.success(service.bindBenefit(cruiseId,offerId,input)); }
    @PostMapping("/{offerId}/prices/{priceId}/submit") @PreAuthorize("hasRole('ACTIVITY_SERVICE_MANAGER') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<OfferView> submit(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@PathVariable UUID priceId,@RequestBody @Valid ReviewPrice input) { return ApiResponse.success(service.reviewPrice(cruiseId,offerId,priceId,"submit",input)); }
    @PostMapping("/{offerId}/prices/{priceId}/approve") @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<OfferView> approve(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@PathVariable UUID priceId,@RequestBody @Valid ReviewPrice input) { return ApiResponse.success(service.reviewPrice(cruiseId,offerId,priceId,"approve",input)); }
    @PostMapping("/{offerId}/prices/{priceId}/return") @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<OfferView> returnPrice(@PathVariable UUID cruiseId,@PathVariable UUID offerId,@PathVariable UUID priceId,@RequestBody @Valid ReviewPrice input) { return ApiResponse.success(service.reviewPrice(cruiseId,offerId,priceId,"return",input)); }
}
