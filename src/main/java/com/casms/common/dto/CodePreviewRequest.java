package com.casms.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodePreviewRequest {

    @NotBlank(message = "entityType không được để trống")
    private String entityType;

    @NotBlank(message = "pattern không được để trống")
    private String pattern;

    private String scopeStrategy;

    private Map<String, String> sampleContext;
}
