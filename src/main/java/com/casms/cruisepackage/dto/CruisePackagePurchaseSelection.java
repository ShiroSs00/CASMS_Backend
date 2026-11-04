package com.casms.cruisepackage.dto;

import com.casms.ship.enums.CabinType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/** Server-authored commercial intent in the existing quote payload; purchased only at real confirmation. */
public record CruisePackagePurchaseSelection(UUID offerId, UUID templateVersionId, UUID priceRevisionId, UUID shipId,
        String logicalCategory, CabinType cabinType,
        @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount,
        String templateName, String templateDescription, int templateVersionNumber, int priceRevisionNumber,
        long commercialRevision, List<Benefit> benefits, UUID logicalCategoryId, UUID cabinMappingId, List<UUID> physicalCabinIds) {
    public CruisePackagePurchaseSelection {
        Objects.requireNonNull(offerId);Objects.requireNonNull(templateVersionId);Objects.requireNonNull(priceRevisionId);
        Objects.requireNonNull(shipId);Objects.requireNonNull(logicalCategory);Objects.requireNonNull(cabinType);
        Objects.requireNonNull(amount);Objects.requireNonNull(templateName);benefits=List.copyOf(benefits);
        physicalCabinIds=physicalCabinIds==null?List.of():List.copyOf(physicalCabinIds);
    }
    public record Occurrence(String resourceType,UUID resourceId,String name) {
        public Occurrence {Objects.requireNonNull(resourceType);Objects.requireNonNull(resourceId);Objects.requireNonNull(name);}
    }
    public record Benefit(UUID sourceBenefitId,String inclusion,UUID onboardActivityId,UUID shoreExcursionId,
            UUID serviceId,Integer quantityLimit,String eligibility,String resourceName,List<Occurrence> occurrences,
            String usageUnit,Boolean requiresReservation,java.time.Instant usageStartsAt,java.time.Instant usageEndsAt,
            String usagePolicy,String serviceUnitCode) {
        public Benefit {
            Objects.requireNonNull(sourceBenefitId);Objects.requireNonNull(inclusion);Objects.requireNonNull(eligibility);Objects.requireNonNull(resourceName);
            occurrences=List.copyOf(occurrences);
        }
    }
}
