package com.casms.ship.dto.response;

import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.enums.RegulationType;
import com.casms.ship.enums.RegulationUnit;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class OperationalRegulationResponse {

    private UUID regulationId;
    private String regulationCode;
    private String name;
    private String description;
    private RegulationType regulationType;
    private RegulationScope scope;
    private String value;
    private RegulationUnit unit;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private RegulationStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
