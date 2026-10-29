package com.casms.common.controller;

import com.casms.common.dto.BusinessCodeRuleResponse;
import com.casms.common.dto.BusinessCodeRuleUpdateRequest;
import com.casms.common.dto.CodePreviewRequest;
import com.casms.common.dto.CodePreviewResponse;
import com.casms.common.service.BusinessCodeRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessCodeRuleControllerTest {

    private MockMvc mockMvc;
    private BusinessCodeRuleService ruleService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ruleService = mock(BusinessCodeRuleService.class);
        BusinessCodeRuleController controller = new BusinessCodeRuleController(ruleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetAllRules() throws Exception {
        BusinessCodeRuleResponse item = BusinessCodeRuleResponse.builder()
                .entityType("TRIP")
                .pattern("{ENTITY}-{SCOPE}/{TYPE}-{SEQ:4}")
                .scopeStrategy("BY_TYPE_AND_YEAR")
                .active(true)
                .updatedAt(Instant.now())
                .build();

        when(ruleService.getAllRules()).thenReturn(List.of(item));

        mockMvc.perform(get("/system/business-code-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].entityType").value("TRIP"))
                .andExpect(jsonPath("$.data[0].pattern").value("{ENTITY}-{SCOPE}/{TYPE}-{SEQ:4}"));
    }

    @Test
    void testGetRuleByEntityType() throws Exception {
        BusinessCodeRuleResponse item = BusinessCodeRuleResponse.builder()
                .entityType("SHIP")
                .pattern("SH-{SHIP_TYPE}-{SEQ:3}")
                .scopeStrategy("GLOBAL")
                .active(true)
                .updatedAt(Instant.now())
                .build();

        when(ruleService.getRuleByEntityType("SHIP")).thenReturn(item);

        mockMvc.perform(get("/system/business-code-rules/SHIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entityType").value("SHIP"))
                .andExpect(jsonPath("$.data.pattern").value("SH-{SHIP_TYPE}-{SEQ:3}"));
    }

    @Test
    void testPreviewCode() throws Exception {
        CodePreviewRequest request = CodePreviewRequest.builder()
                .entityType("TRIP")
                .pattern("{ENTITY}-{SCOPE}/{TYPE}-{SEQ:4}")
                .sampleContext(Map.of("SCOPE", "EX", "TYPE", "IT"))
                .build();

        CodePreviewResponse response = CodePreviewResponse.builder()
                .previewCode("TRIP-EX/IT-0001")
                .simulatedKey("TRIP_EX_IT_2026")
                .valid(true)
                .warnings(List.of())
                .build();

        when(ruleService.previewCode(any(CodePreviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/system/business-code-rules/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.previewCode").value("TRIP-EX/IT-0001"))
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void testUpdateRule() throws Exception {
        BusinessCodeRuleUpdateRequest request = BusinessCodeRuleUpdateRequest.builder()
                .pattern("TRIP-{SCOPE}-{YYYY}-{SEQ:4}")
                .scopeStrategy("BY_YEAR")
                .description("Updated trip format")
                .build();

        BusinessCodeRuleResponse response = BusinessCodeRuleResponse.builder()
                .entityType("TRIP")
                .pattern("TRIP-{SCOPE}-{YYYY}-{SEQ:4}")
                .scopeStrategy("BY_YEAR")
                .description("Updated trip format")
                .active(true)
                .build();

        when(ruleService.updateRule(eq("TRIP"), any(BusinessCodeRuleUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/system/business-code-rules/TRIP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pattern").value("TRIP-{SCOPE}-{YYYY}-{SEQ:4}"));
    }
}
