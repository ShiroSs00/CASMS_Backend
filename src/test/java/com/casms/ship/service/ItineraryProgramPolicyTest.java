package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ItineraryProgramPolicyTest {
    private final ItineraryProgramPolicy policy = new ItineraryProgramPolicy(mock(JdbcTemplate.class));
    private final Instant arrival = Instant.parse("2030-02-01T08:00:00Z");
    private final Instant departure = Instant.parse("2030-02-01T18:00:00Z");

    @Test void allowsInclusiveCruiseBoundsAndRejectsMissingInvertedOrOutsideTimes() {
        assertDoesNotThrow(() -> ItineraryProgramPolicy.requireWindow(arrival, departure, arrival, departure, "WINDOW"));
        assertCode("WINDOW", () -> ItineraryProgramPolicy.requireWindow(null, departure, arrival, departure, "WINDOW"));
        assertCode("WINDOW", () -> ItineraryProgramPolicy.requireWindow(departure, arrival, arrival, departure, "WINDOW"));
        assertCode("WINDOW", () -> ItineraryProgramPolicy.requireWindow(arrival.minusSeconds(1), departure, arrival, departure, "WINDOW"));
        assertCode("WINDOW", () -> ItineraryProgramPolicy.requireWindow(arrival, departure.plusSeconds(1), arrival, departure, "WINDOW"));
    }

    @Test void shoreWindowAccountsForTenderAndRequiresReturnBeforeAllAboard() {
        var stop = Map.<String,Object>of("planned_arrival", arrival, "planned_departure", departure,
                "all_aboard_time", departure.minusSeconds(3600), "tender_transfer_minutes", 30);
        var cruise = Map.<String,Object>of("departure_datetime", arrival.minusSeconds(3600),
                "return_datetime", departure.plusSeconds(3600));
        assertDoesNotThrow(() -> policy.validateExcursionTimes(stop, arrival.plusSeconds(1800), departure.minusSeconds(3601), cruise));
        assertCode("EXCURSION_OUTSIDE_PORT_CALL", () -> policy.validateExcursionTimes(stop, arrival.plusSeconds(1799), departure.minusSeconds(3601), cruise));
        assertCode("EXCURSION_OUTSIDE_PORT_CALL", () -> policy.validateExcursionTimes(stop, arrival.plusSeconds(1800), departure.minusSeconds(3600), cruise));
        var shortCruise = Map.<String,Object>of("departure_datetime", arrival.plusSeconds(7200), "return_datetime", departure);
        assertCode("EXCURSION_OUTSIDE_CRUISE", () -> policy.validateExcursionTimes(stop, arrival.plusSeconds(1800), departure.minusSeconds(3601), shortCruise));
    }

    @Test void requiresExplicitCapacityPriceCurrencyAndLocation() {
        assertDoesNotThrow(() -> ItineraryProgramPolicy.requireDetails(20, BigDecimal.ZERO, "USD", "Open deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(null, BigDecimal.ZERO, "USD", "Deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(0, BigDecimal.ZERO, "USD", "Deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(20, null, "USD", "Deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(20, BigDecimal.ONE.negate(), "USD", "Deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(20, BigDecimal.ZERO, "VND", "Deck"));
        assertCode("PROGRAM_DETAILS_REQUIRED", () -> ItineraryProgramPolicy.requireDetails(20, BigDecimal.ZERO, "USD", "  "));
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getCode());
    }
}
