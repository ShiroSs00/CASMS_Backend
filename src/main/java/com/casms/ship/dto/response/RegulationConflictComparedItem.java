package com.casms.ship.dto.response;

import com.casms.ship.enums.RegulationScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationConflictComparedItem {
    private UUID regulationId;
    private String code;
    private RegulationScope scope;
    private String value;
    private String unit;
}
