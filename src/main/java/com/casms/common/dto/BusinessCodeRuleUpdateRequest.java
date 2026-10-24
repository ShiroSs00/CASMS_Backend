package com.casms.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCodeRuleUpdateRequest {

    @NotBlank(message = "pattern không được để trống")
    private String pattern;

    @NotBlank(message = "scopeStrategy không được để trống")
    @Pattern(regexp = "GLOBAL|BY_YEAR|BY_TYPE_AND_YEAR|BY_PORT",
             message = "scopeStrategy chỉ chấp nhận: GLOBAL, BY_YEAR, BY_TYPE_AND_YEAR, BY_PORT")
    private String scopeStrategy;

    private String description;
}
