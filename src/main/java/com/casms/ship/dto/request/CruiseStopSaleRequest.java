package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseStopSaleRequest {
    private Long expectedRevision;

    @NotBlank(message = "Lý do dừng bán là bắt buộc")
    private String reason;

    private UUID packageId;
}
