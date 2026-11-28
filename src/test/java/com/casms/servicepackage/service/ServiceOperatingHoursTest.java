package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.controller.ServicepackageController.ServiceCatalogDto;
import com.casms.servicepackage.dto.ServiceOperatingWindow;
import com.casms.servicepackage.enums.ServiceOperatingPeriod;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServiceOperatingHoursTest {
    private final ServiceOperatingHours hours = new ServiceOperatingHours("06:00", "18:00", "22:00");

    @Test
    void exactHoursAllowAdjacentWindowsButRejectAGapAndClosingStart() {
        var adjacent = List.of(window("09:30", "10:00"), window("10:00", "11:15"));
        assertDoesNotThrow(() -> hours.requireOpen(adjacent, null, i("09:30"), i("11:15"), ZoneOffset.UTC));
        assertClosed(() -> hours.requireOpen(adjacent, null, i("11:15"), null, ZoneOffset.UTC));

        var separated = List.of(window("09:30", "10:00"), window("10:01", "11:15"));
        assertClosed(() -> hours.requireOpen(separated, null, i("09:45"), i("10:30"), ZoneOffset.UTC));
    }

    @Test
    void overnightWindowCoversMidnightUntilItsClosingBoundary() {
        var windows = List.of(window("23:00", "02:30"));
        assertDoesNotThrow(() -> hours.requireOpen(windows, null, i("23:30"),
                Instant.parse("2026-11-29T02:30:00Z"), ZoneOffset.UTC));
        assertClosed(() -> hours.requireOpen(windows, null, i("23:30"),
                Instant.parse("2026-11-29T02:31:00Z"), ZoneOffset.UTC));
    }

    @Test
    void exactConfigurationOverridesLegacyAndEmptyExactListClearsRestriction() {
        var legacy = Set.of(ServiceOperatingPeriod.DAYTIME);
        assertTrue(ServiceOperatingHours.isRestricted(null, legacy));
        assertFalse(ServiceOperatingHours.isRestricted(List.of(), legacy));
        assertClosed(() -> hours.requireOpen(null, legacy, i("23:00"), null, ZoneOffset.UTC));
        assertDoesNotThrow(() -> hours.requireOpen(List.of(), legacy, i("23:00"), null, ZoneOffset.UTC));
        assertDoesNotThrow(() -> hours.requireOpen(
                List.of(window("22:30", "23:30")), legacy, i("23:00"), null, ZoneOffset.UTC));
    }

    @Test
    void rechecksMembershipWhenFallBackRepeatsEarlierLocalTimes() {
        ZoneId ny = ZoneId.of("America/New_York");
        var windows = List.of(window("01:30", "02:30"));
        Instant start = Instant.parse("2026-11-01T05:45:00Z");
        assertDoesNotThrow(() -> hours.requireOpen(
                windows, null, start, Instant.parse("2026-11-01T06:00:00Z"), ny));
        assertClosed(() -> hours.requireOpen(
                windows, null, start, Instant.parse("2026-11-01T06:45:00Z"), ny));
    }

    @Test
    void equalBoundsFailValidationAndJsonUsesLocalTimeStrings() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(window("10:00", "10:00")).isEmpty());
            assertTrue(factory.getValidator().validate(window("22:45", "02:15")).isEmpty());
        }
        var mapper = new ObjectMapper().findAndRegisterModules();
        var serialized = mapper.readTree(mapper.writeValueAsString(window("22:45", "02:15")));
        assertTrue(serialized.get("startsAt").isTextual());
        assertEquals(window("22:45", "02:15"), mapper.treeToValue(serialized, ServiceOperatingWindow.class));
    }

    @Test
    void catalogueValidatesExactWindowsWithoutRequiringLegacyPeriods() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        String payload = """
                {"serviceName":"Buffet","serviceType":"DINING",
                 "operatingWindows":[{"startsAt":"06:30","endsAt":"09:00"}]}
                """;
        var valid = mapper.readValue(payload, ServiceCatalogDto.class);
        var invalid = mapper.readValue(payload.replace("09:00", "06:30"), ServiceCatalogDto.class);

        assertNull(valid.operatingPeriods());
        assertEquals(List.of(window("06:30", "09:00")), valid.operatingWindows());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(valid).isEmpty());
            assertFalse(factory.getValidator().validate(invalid).isEmpty());
        }
    }

    private static void assertClosed(org.junit.jupiter.api.function.Executable check) {
        var error = assertThrows(BusinessException.class, check);
        assertEquals("SERVICE_OUTSIDE_OPERATING_HOURS", error.getCode());
    }

    private static ServiceOperatingWindow window(String start, String end) {
        return new ServiceOperatingWindow(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Instant i(String time) {
        return Instant.parse("2026-11-28T" + time + ":00Z");
    }
}
