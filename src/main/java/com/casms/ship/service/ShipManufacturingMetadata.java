package com.casms.ship.service;

import com.casms.ship.dto.response.ShipDesignMetadata;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

record ShipManufacturingMetadata(String code, String yard, String country, Integer year) {
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    static ShipManufacturingMetadata read(JsonNode manifest) {
        String country = text(manifest, "buildCountry", 2);
        JsonNode year = manifest.path("buildYear");
        return new ShipManufacturingMetadata(
                text(manifest, "manufacturerShipCode", 80),
                text(manifest, "shipyard", 150),
                country != null && ISO_COUNTRIES.contains(country) ? country : null,
                year.isIntegralNumber() && year.canConvertToInt() && year.intValue() >= 1
                        && year.intValue() <= Year.now(ZoneOffset.UTC).getValue() ? year.intValue() : null);
    }

    private static String text(JsonNode manifest, String field, int max) {
        JsonNode value = manifest.path(field);
        if (!value.isTextual()) return null;
        String text = value.textValue().strip();
        return text.isEmpty() || text.length() > max || text.indexOf(0) >= 0 ? null : text;
    }

    List<String> invalidFields() {
        List<String> fields = new ArrayList<>();
        if (code == null) fields.add("manufacturerShipCode");
        if (yard == null) fields.add("shipyard");
        if (country == null) fields.add("buildCountry");
        if (year == null) fields.add("buildYear");
        return fields;
    }

    ShipDesignMetadata response() {
        return new ShipDesignMetadata(code, yard, country, year);
    }
}
