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

    private List<UUID> packageIds;

    @NotBlank(message = "fingerprint là bắt buộc")
    private String fingerprint;

    private String eventIdentity;
}
