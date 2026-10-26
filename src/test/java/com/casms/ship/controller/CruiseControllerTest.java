package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.service.CruisePackageService;
import com.casms.ship.service.CruiseService;
import com.casms.ship.service.CruiseStaffService;
import com.casms.trip.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CruiseControllerTest {

    @Mock private CruiseService cruiseService;
    @Mock private CruiseStaffService staffService;
    @Mock private CruisePackageService packageService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private com.casms.ship.service.ScheduleMaterializationService scheduleMaterializationService;
    @Mock private com.casms.ship.service.OperationalProgramMappingService operationalProgramMappingService;
    @Mock private com.casms.ship.service.CruiseImpactAssessmentService cruiseImpactAssessmentService;
    @Mock private CasmsPrincipal principal;

    private CruiseController controller;

    @BeforeEach
    void setUp() {
        controller = new CruiseController(cruiseService, staffService, packageService, idempotencyService, scheduleMaterializationService, operationalProgramMappingService, cruiseImpactAssessmentService);
    }

    @Test
    void create_requiresIdempotencyKey() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.create(null, new CruiseCreateRequest(), principal));

        assertEquals("INVALID_INPUT", exception.getCode());
        verify(idempotencyService, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void create_delegatesToSharedIdempotencyServiceWithCruiseOperation() {
        UUID actorId = UUID.randomUUID();
        CruiseCreateRequest request = new CruiseCreateRequest();
        CruiseResponse expected = new CruiseResponse();
        expected.setCruiseId(UUID.randomUUID());
        when(principal.getUserId()).thenReturn(actorId);
        when(idempotencyService.execute(
                eq("cruise-create-001"), eq(actorId), eq("CREATE_CRUISE"), same(request),
                eq(CruiseResponse.class), any())).thenReturn(expected);

        ApiResponse<CruiseResponse> response = controller.create("cruise-create-001", request, principal);

        assertSame(expected, response.getData());
        assertEquals("Created Cruise successfully", response.getMessage());
        verify(cruiseService, never()).create(request);
    }
}
