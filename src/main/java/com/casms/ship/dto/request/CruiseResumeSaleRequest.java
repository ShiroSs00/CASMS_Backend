package com.casms.ship.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CruiseResumeSaleRequest {
    private Long expectedRevision;
    private String reason;
    private String evidence;
    private String scope;
    private String eventIdentity;
    private UUID packageId;
}
