package com.casms.common.service;

import com.casms.common.dto.BusinessCodeRuleUpdateRequest;
import com.casms.common.dto.CodePreviewRequest;
import com.casms.common.dto.CodePreviewResponse;
import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BusinessCodeRuleServiceTest {

    private JdbcTemplate jdbcTemplate;
    private BusinessCodeRuleService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new BusinessCodeRuleService(jdbcTemplate);
    }

    @Test
    void testPreviewCodeWithValidPattern() {
        CodePreviewRequest request = CodePreviewRequest.builder()
                .entityType("TRIP")
                .pattern("{ENTITY}-{SCOPE}/{TYPE}-{SEQ:4}")
                .scopeStrategy("BY_TYPE_AND_YEAR")
                .sampleContext(Map.of("SCOPE", "EX", "TYPE", "IT"))
                .build();

        CodePreviewResponse response = service.previewCode(request);

        assertNotNull(response);
        assertEquals("TRIP-EX/IT-0001", response.getPreviewCode());
        assertTrue(response.isValid());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void testPreviewCodeWithoutSequenceProducesWarning() {
        CodePreviewRequest request = CodePreviewRequest.builder()
                .entityType("SHIP")
                .pattern("SHIP-STATIC-CODE")
                .scopeStrategy("GLOBAL")
                .build();

        CodePreviewResponse response = service.previewCode(request);

        assertNotNull(response);
        assertFalse(response.isValid());
        assertFalse(response.getWarnings().isEmpty());
        assertTrue(response.getWarnings().get(0).contains("Thiếu token số thứ tự"));
    }

    @Test
    void testUpdateRuleFailsWhenMissingSequenceToken() {
        BusinessCodeRuleUpdateRequest request = BusinessCodeRuleUpdateRequest.builder()
                .pattern("INVALID-PATTERN-NO-SEQ")
                .scopeStrategy("GLOBAL")
                .description("Invalid rule")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateRule("TRIP", request));
        assertEquals("INVALID_PATTERN", ex.getCode());
    }

    @Test
    void testGenerateCodeFormatsSequenceProperly() {
        int currentYear = LocalDate.now().getYear();
        String sqlSelect = "SELECT pattern, scope_strategy FROM business_code_rule WHERE UPPER(entity_type) = ? AND is_active = true";
        when(jdbcTemplate.queryForList(sqlSelect, "CRUISE")).thenReturn(List.of(
                Map.of("pattern", "CR-{YYYY}-{SHIP}-{SEQ:3}", "scope_strategy", "BY_YEAR")
        ));

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("CRUISE_" + currentYear))).thenReturn(7L);

        String generated = service.generateCode("CRUISE", Map.of("SHIP", "SH01"));

        assertEquals("CR-" + currentYear + "-SH01-007", generated);
    }
}
