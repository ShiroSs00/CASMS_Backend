package com.casms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCodeRuleResponse {
    private String entityType;
    private String pattern;
    private String scopeStrategy;
    private String description;
    private boolean active;
    private Instant updatedAt;
    private List<TokenMetadata> supportedTokens;
    private Map<String, String> defaultSampleContext;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenMetadata {
        private String key;
        private String label;
        private String example;
    }
}
