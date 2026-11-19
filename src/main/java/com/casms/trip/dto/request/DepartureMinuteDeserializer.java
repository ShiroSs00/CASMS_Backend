package com.casms.trip.dto.request;

import com.casms.trip.service.TripDepartureWindow;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class DepartureMinuteDeserializer extends JsonDeserializer<LocalTime> {

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalTime) context.handleUnexpectedToken(LocalTime.class, parser);
        }
        String value = parser.getText().trim();
        try {
            if (value.contains("T")) {
                return TripDepartureWindow.shipMinute(OffsetDateTime.parse(value).toInstant());
            }
            return LocalTime.parse(value).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException exception) {
            throw context.weirdStringException(value, LocalTime.class,
                    "Expected a ship-local time (HH:mm) or an ISO-8601 timestamp with offset");
        }
    }
}
