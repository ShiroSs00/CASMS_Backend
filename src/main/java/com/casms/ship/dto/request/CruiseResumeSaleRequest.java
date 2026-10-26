package com.casms.ship.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseResumeSaleRequest {
    private Long expectedRevision;
    private String reason;
    private UUID packageId;
}
