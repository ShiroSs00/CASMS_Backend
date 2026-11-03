package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseOpenSaleRequest {

    @NotNull(message = "expectedRevision là bắt buộc")
    private Long expectedRevision;

    @NotBlank(message = "saleScope là bắt buộc (CABIN_ONLY, SELECTED_PACKAGES, WHOLE_CRUISE)")
    private String saleScope; // WHOLE_CRUISE, CABIN_ONLY, SELECTED_PACKAGES

    @io.swagger.v3.oas.annotations.media.Schema(description = "Legacy TourPackage IDs, không phải Template/Version/Departure Offer IDs. Bắt buộc khi SELECTED_PACKAGES.")
    private List<UUID> packageIds;
    // Fare Offer identifiers are distinct from legacy TourPackage packageIds.
    @com.fasterxml.jackson.annotation.JsonAlias("cruisePackageOfferIds")
    @io.swagger.v3.oas.annotations.media.Schema(description = "Departure Cruise Package Offer IDs được chọn tường minh; nhận alias cruisePackageOfferIds. Không diễn giải packageIds thành Offer IDs.")
    private List<UUID> farePackageOfferIds;

    @NotBlank(message = "fingerprint là bắt buộc")
    private String fingerprint;

    private String eventIdentity;
}
