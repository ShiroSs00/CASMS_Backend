package com.casms.ship.dto.request;

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
    private Long expectedRevision;
    private List<UUID> packageIds;
}
