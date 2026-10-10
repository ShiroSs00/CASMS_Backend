package com.casms.ship.dto;

import com.casms.ship.entity.CruiseStatus;
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
