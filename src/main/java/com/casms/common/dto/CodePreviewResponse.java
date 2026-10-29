package com.casms.common.dto;

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
public class CodePreviewResponse {
    private String previewCode;
    private String simulatedKey;
    private boolean valid;
    private List<String> warnings;
}
