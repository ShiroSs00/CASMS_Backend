package com.casms.cruisepackage.dto;

import com.casms.cruisepackage.entity.*;
import com.casms.ship.enums.CabinType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CruisePackageContracts {
    private CruisePackageContracts() {}
    public record CreateTemplate(@NotBlank @Size(max=50) String code) {}
    public enum Inclusion { INCLUDED, LIMITED_INCLUDED, OPTIONAL, NOT_INCLUDED }
    public enum ResourceType { ACTIVITY, EXCURSION, SERVICE }
    public record BenefitInput(@NotNull ResourceType resourceType, @NotNull UUID resourceId,
            @NotNull Inclusion inclusion, @Positive Integer quantityLimit,
            @NotBlank @Size(max=4000) String eligibility,
            @Size(max=30) String usageUnit,@NotNull Boolean requiresReservation,
            @Size(max=20) String usagePolicy,@Size(max=30) String serviceUnitCode,
            Instant usageStartsAt,Instant usageEndsAt) {}
    public record CreateVersion(@NotNull @PositiveOrZero Long expectedTemplateRevision,
            @NotBlank @Size(max=200) String name, @Size(max=10000) String description,
            @NotEmpty @Size(max=50) List<@NotBlank @Size(max=100) String> logicalCategories,
            @NotNull @Size(max=200) List<@NotNull @Valid BenefitInput> benefits,
            @Size(max=50) List<@NotNull UUID> reusedLogicalCategoryIds) {}
    public record Revision(@NotNull @PositiveOrZero Long expectedRevision) {}
    public record OfferInput(@NotNull UUID templateVersionId,
            @NotBlank @Size(max=100) String logicalCategory, @NotNull UUID logicalCategoryId, @NotNull CabinType proposedCabinType,
            @NotNull Instant salesStart, @NotNull Instant salesEnd) {}
    public record UpgradeOffer(@NotNull @PositiveOrZero Long expectedRevision, @NotNull @Valid OfferInput offer) {}
    public record BindBenefit(@NotNull @PositiveOrZero Long expectedOfferRevision,
            @NotNull UUID benefitId, @NotNull UUID occurrenceId) {}
    public record CreatePrice(@NotNull @PositiveOrZero Long expectedOfferRevision,
            @NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal amount) {}
    public record ReviewPrice(@NotNull @PositiveOrZero Long expectedRevision, @Size(max=4000) String reason) {}
    public record Blocker(String code, String message, UUID dependencyId) {}
    public record ResourceOption(UUID id, String name,String unitCode) {
        public ResourceOption(UUID id,String name){this(id,name,null);}
    }
    public record AvailableCruisePackage(UUID cruiseId,CruisePackagePurchaseSelection selection,String currencyCode,String priceUnit) {}
    public record CruisePackageCatalog(UUID cruiseId,boolean fareManaged,List<AvailableCruisePackage> offers) {}
    public record Eligibility(boolean eligible, List<Blocker> blockers) {}
    public record VersionView(CruisePackageTemplateVersion version, List<CruisePackageBenefit> benefits, Map<UUID,String> resourceNames,
            List<com.casms.cruisepackage.service.CruisePackageCabinMappingService.Category> logicalCategoryReferences) {}
    public record CruisePackageContext(UUID cruiseId, UUID shipId, String planStatus, boolean canConfigure, boolean canCreatePrice, boolean canReviewPrice) {}
    public record OfferView(DepartureCruisePackage offer, VersionView template, List<DepartureCruisePackageBenefit> bindings, Map<UUID,String> bindingNames, List<DepartureCruisePackagePrice> prices,
            UUID currentApprovedPriceRevisionId, Eligibility eligibility,
            com.casms.cruisepackage.service.CruisePackageCabinMappingService.Assessment cabinMapping) {}
}
