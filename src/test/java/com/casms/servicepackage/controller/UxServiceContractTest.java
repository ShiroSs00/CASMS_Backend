package com.casms.servicepackage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.ship.entity.Cruise;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class UxServiceContractTest {
    @Test
    void serviceReadExposesExistingUnitsWithoutCreatingDefaults() throws Exception {
        var s = new Service();
        s.setServiceId(UUID.randomUUID());
        s.setStatus(ServiceStatus.ACTIVE);
        s.setServiceName("Test-only service");
        s.setServiceCode("TEST-SERVICE");
        s.setServiceType("SPA");
        s.setPrice(new BigDecimal("12.3456"));
        s.setCurrencyCode("USD");
        s.setRequiresSchedule(true);
        s.setUnitCode("SESSION");
        s.setApplicabilityScope("PASSENGER");
        var json = new ObjectMapper().valueToTree(ServicepackageController.ServiceView.of(s));
        assertEquals("SESSION", json.get("unitCode").asText());
        assertEquals("PASSENGER", json.get("applicabilityScope").asText());
        assertTrue(json.get("requiresSchedule").asBoolean());
        assertEquals("USD", json.get("currency").asText());
        assertEquals(0, new BigDecimal(json.get("price").asText()).compareTo(s.getPrice()));
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        var schedule = new ServiceSchedule();
        schedule.setServiceScheduleId(UUID.randomUUID());
        schedule.setService(s);
        schedule.setCruise(cruise);
        schedule.setStartsAt(Instant.parse("2030-01-01T01:00:00Z"));
        schedule.setEndsAt(Instant.parse("2030-01-01T02:00:00Z"));
        schedule.setLocation("Test-only location");
        schedule.setCapacity(5);
        schedule.setStatus("OPEN");
        var mapper = new ObjectMapper().findAndRegisterModules();
        mapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Files.writeString(
                Path.of("target/ux-v2-service-contract.json"),
                mapper.writeValueAsString(
                        Map.of(
                                "service", ServicepackageController.ServiceView.of(s),
                                "schedule",
                                        ServicepackageController.ServiceScheduleView.of(
                                                schedule, 2))));
        s.setUnitCode(null);
        assertNull(ServicepackageController.ServiceView.of(s).unitCode());
    }

    @Test
    void scheduleReadIdentifiesCruiseAndPreservesAvailability() {
        var s = new Service();
        s.setServiceId(UUID.randomUUID());
        var c = new Cruise();
        c.setCruiseId(UUID.randomUUID());
        var schedule = new ServiceSchedule();
        schedule.setServiceScheduleId(UUID.randomUUID());
        schedule.setService(s);
        schedule.setCruise(c);
        schedule.setCapacity(5);
        var view = ServicepackageController.ServiceScheduleView.of(schedule, 2);
        assertEquals(c.getCruiseId(), view.cruiseId());
        assertEquals(s.getServiceId(), view.serviceId());
        assertEquals(2, view.remainingCapacity());
        assertEquals(5, view.capacity());
    }
}
