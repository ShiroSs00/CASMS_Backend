package com.casms.ship.dto.response;

import com.casms.ship.enums.CruiseStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CruiseSummaryResponse {
    private UUID cruiseId;
    private String cruiseCode;
    private String cruiseName;
    private CruiseStatus status;
    private Instant departureDatetime;
    private Instant returnDatetime;
}
