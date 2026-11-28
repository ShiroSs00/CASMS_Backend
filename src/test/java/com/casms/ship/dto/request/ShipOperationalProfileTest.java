package com.casms.ship.dto.request;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class ShipOperationalProfileTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void retainsLatestSurveyOnProvisioningAndReplacement() throws Exception {
        var previous = read("""
                {"shipyard":"Legacy yard", "classificationDate":"2024-05-20"}
                """);
        var submitted = read("""
                {"classificationDate":"2025-05-20", "classificationExpiryDate":"2026-05-20"}
                """);

        var provisioned = submitted.withLegacyManufacturing(null);
        var replaced = submitted.withLegacyManufacturing(previous);

        assertEquals(LocalDate.of(2025, 5, 20), provisioned.classificationDate());
        assertEquals(provisioned.classificationDate(), replaced.classificationDate());
        assertEquals("Legacy yard", replaced.shipyard());
        assertEquals("2025-05-20", mapper.valueToTree(replaced).get("classificationDate").asText());
    }

    @Test
    void acceptsClassificationDateInProfileAndProvisioningPayloads() throws Exception {
        var profile = read("""
                {"classificationDate":"2025-05-20", "classificationExpiryDate":"2026-05-20"}
                """);
        var provisioning = mapper.readValue("""
                {"shipName":"Test Ship", "profile":{"classificationDate":"2025-05-20"}}
                """, ShipFromTypeRequest.class);

        assertEquals(LocalDate.of(2025, 5, 20), profile.classificationDate());
        assertEquals(profile.classificationDate(), provisioning.profile().classificationDate());
        var response = mapper.valueToTree(profile);
        assertEquals("2025-05-20", response.get("classificationDate").asText());
        assertFalse(response.has("lastClassificationDate"));
        assertThrows(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,
                () -> read("{\"lastClassificationDate\":\"2025-05-20\"}"));
    }

    @Test
    void legacyPayloadOmitsDateAndFullReplacementCanClearIt() throws Exception {
        var legacy = read("""
                {"classificationExpiryDate":"2026-05-20"}
                """);
        var previous = read("""
                {"classificationDate":"2025-05-20"}
                """);

        assertNull(legacy.classificationDate());
        assertFalse(mapper.valueToTree(legacy).has("classificationDate"));
        assertNull(legacy.withLegacyManufacturing(previous).classificationDate());
    }

    @Test
    void validatesLatestSurveyAndExpiryOrderingOnlyWhenProvided() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            LocalDate today = LocalDate.now();
            var future = read("""
                    {"classificationDate":"%s"}
                    """.formatted(today.plusDays(2)));
            var expiresEarlier = read("""
                    {"classificationDate":"%s", "classificationExpiryDate":"%s"}
                    """.formatted(today, today.minusDays(1)));
            var sameDay = read("""
                    {"classificationDate":"%s", "classificationExpiryDate":"%s"}
                    """.formatted(today, today));

            assertTrue(validator.validate(future).stream()
                    .anyMatch(error -> error.getPropertyPath().toString().equals("classificationDate")));
            assertTrue(validator.validate(expiresEarlier).stream().anyMatch(
                    error -> error.getPropertyPath().toString().equals("expiryNotBeforeLastClassification")));
            assertTrue(validator.validate(sameDay).isEmpty());
            assertTrue(validator.validate(read("{\"classificationDate\":\"2025-05-20\"}")).isEmpty());
            assertTrue(validator.validate(read("{\"classificationExpiryDate\":\"2026-05-20\"}")).isEmpty());
        }
    }

    private ShipOperationalProfile read(String json) throws Exception {
        return mapper.readValue(json, ShipOperationalProfile.class);
    }
}
