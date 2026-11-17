package com.casms.ship.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public record ShipOperationalProfile(
        @Size(max = 80) String manufacturerShipCode,
        @Pattern(regexp = "[0-9]{7}") String imoNumber,
        @Size(max = 150) String shipyard,
        @Pattern(regexp = "[A-Z]{2}") String buildCountry,
        @Min(1) @Max(9999) Integer buildYear,
        @PastOrPresent LocalDate deliveryDate,
        @Pattern(regexp = "[A-Z]{2}") String registrationCountry,
        @Size(max = 80) String registrationNumber,
        @Size(max = 150) String classificationSociety,
        LocalDate classificationExpiryDate,
        @Size(max = 100) List<@NotBlank @Size(max = 1000) String> certificateReferences,
        @Size(max = 100) List<@NotBlank @Size(max = 1000) String> warrantyReferences,
        @Size(max = 100) List<@NotBlank @Size(max = 1000) String> maintenanceReferences) {

    public ShipOperationalProfile {
        manufacturerShipCode = trim(manufacturerShipCode);
        imoNumber = upper(imoNumber);
        if (imoNumber != null && imoNumber.startsWith("IMO ")) {
            imoNumber = trim(imoNumber.substring(4));
        }
        shipyard = trim(shipyard);
        buildCountry = upper(buildCountry);
        registrationCountry = upper(registrationCountry);
        registrationNumber = upper(registrationNumber);
        classificationSociety = trim(classificationSociety);
    }

    @AssertTrue(message = "Registration country and registration number must be provided together")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isRegistrationComplete() {
        return (registrationCountry == null) == (registrationNumber == null);
    }

    public ShipOperationalProfile withLegacyManufacturing(ShipOperationalProfile previous) {
        return new ShipOperationalProfile(
                previous == null ? null : previous.manufacturerShipCode(),
                imoNumber,
                previous == null ? null : previous.shipyard(),
                previous == null ? null : previous.buildCountry(),
                previous == null ? null : previous.buildYear(),
                deliveryDate, registrationCountry, registrationNumber, classificationSociety,
                classificationExpiryDate, certificateReferences, warrantyReferences, maintenanceReferences);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
