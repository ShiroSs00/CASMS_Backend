package com.casms.trip.dto.request;

import com.casms.trip.dto.response.TripDetailResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepartureMinuteJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createAndUpdateAcceptLocalTimesAndLegacyTimestamps() throws Exception {
        String json = """
                {"earliestDeparture":"08:00:59","latestDeparture":"2026-10-01T12:30:42+09:00"}
                """;
        TripCreateRequest create = mapper.readValue(json, TripCreateRequest.class);
        TripUpdateRequest update = mapper.readValue(json, TripUpdateRequest.class);
        TripDraftPatchRequest patch = mapper.readValue(json, TripDraftPatchRequest.class);

        assertEquals(LocalTime.of(8, 0), create.getEarliestDeparture());
        assertEquals(LocalTime.of(10, 30), create.getLatestDeparture());
        assertEquals(create.getEarliestDeparture(), update.getEarliestDeparture());
        assertEquals(create.getLatestDeparture(), update.getLatestDeparture());
        assertEquals(create.getEarliestDeparture(), patch.getEarliestDeparture());
        assertEquals(create.getLatestDeparture(), patch.getLatestDeparture());
    }

    @Test
    void detailReturnsHourAndMinuteStrings() {
        TripDetailResponse response = new TripDetailResponse();
        response.setEarliestDeparture(LocalTime.of(8, 0));
        response.setLatestDeparture(LocalTime.of(10, 30));

        var json = mapper.valueToTree(response);
        assertEquals("08:00", json.get("earliestDeparture").asText());
        assertEquals("10:30", json.get("latestDeparture").asText());
    }
}
