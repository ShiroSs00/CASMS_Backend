package com.casms.ship.dto.request;

import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationType;
import com.casms.ship.enums.RegulationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class OperationalRegulationRequest {

    @Size(max = 30, message = "Regulation code must not exceed 30 characters")
    private String regulationCode;

    @NotBlank(message = "Regulation name is required")
    @Size(max = 150, message = "Regulation name must not exceed 150 characters")
    private String name;

    private String description;

    @NotNull(message = "Regulation type is required")
    private RegulationType regulationType;

    @NotNull(message = "Regulation scope is required")
    private RegulationScope scope;

    @NotBlank(message = "Regulation value is required")
    @Size(max = 100, message = "Regulation value must not exceed 100 characters")
    private String value;

    private RegulationUnit unit;

    @NotNull(message = "Effective-from date is required")
    private Instant effectiveFrom;

    private Instant effectiveTo;
}
