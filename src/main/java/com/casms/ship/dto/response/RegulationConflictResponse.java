package com.casms.ship.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationConflictResponse {

    private String context;
    private String severity;
    private Boolean canProceed;
    private String code;
    private String message;
    private List<RegulationConflictComparedItem> comparedWith;
}
