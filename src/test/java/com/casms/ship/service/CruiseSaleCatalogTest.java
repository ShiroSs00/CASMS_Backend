package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSellabilityResponse;
import com.casms.ship.enums.CruiseStatus;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

class CruiseSaleCatalogTest {
    @Test
    void saleFilteringPrecedesPaginationAndUsesAuthoritativeState() {
        CruiseService service = mock(CruiseService.class, CALLS_REAL_METHODS);
        List<CruiseResponse> rows =
                java.util.stream.IntStream.range(0, 5)
                        .mapToObj(
                                i -> {
                                    var c = new CruiseResponse();
                                    c.setCruiseId(UUID.randomUUID());
                                    return c;
                                })
                        .toList();
        doReturn(new PageImpl<>(rows))
                .when(service)
                .list(eq(CruiseStatus.PUBLISHED), any(), any(), any(), any(), any(), any());
        String[] states = {"NOT_YET_OPEN", "OPEN_FOR_SALE", "STOPPED", "OPEN_FOR_SALE", "SOLD_OUT"};
        for (int i = 0; i < rows.size(); i++)
            doReturn(CruiseSellabilityResponse.builder().saleState(states[i]).build())
                    .when(service)
                    .getPublicSellability(rows.get(i).getCruiseId());
        var result = service.listOpenForSale(null, null, null, null, null, PageRequest.of(1, 1));
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(rows.get(3).getCruiseId(), result.getContent().getFirst().getCruiseId());
        assertTrue(
                service.listOpenForSale(null, null, null, null, null, PageRequest.of(2, 1))
                        .isEmpty());
    }
}
