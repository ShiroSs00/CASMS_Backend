package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.PortFacts;
import com.casms.ship.dto.PortWindowContract.Check;
import com.casms.ship.dto.request.CruiseDelayRequest;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.ship.util.PortTimeZoneHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CruiseDelayPostgresIntegrationTest {
    @Autowired CruiseDelayService service;
    @Autowired CruiseRepository cruises;
    @Autowired ShipRepository ships;
    @Autowired PortRepository ports;
    @Autowired ItineraryRepository itineraries;
    @Autowired ItineraryDayRepository days;
    @Autowired CruiseStopRepository stops;
    @SpyBean CruiseService cruiseService;
    @SpyBean CruiseAuditService audit;
    @MockBean PortWindowEvaluator windows;

    @Test
    void concurrentRetryPersistsOnceAndSubsequentDelayAndRollbackPreserveHistory() throws Exception {
        Port port = new Port();
        port.setPortName("Delay integration port");
        port.setPortCode("VN" + UUID.randomUUID().toString().substring(0, 5).toUpperCase());
        port.setCountryCode("VN");
        port.setTimeZoneId(PortTimeZoneHelper.SHIP_TIME_ZONE_ID);
        port.setStatus(PortStatus.ACTIVE);
        port = ports.save(port);

        Ship ship = new Ship();
        ship.setShipCode("DELAY-" + UUID.randomUUID().toString().substring(0, 8));
        ship.setShipName("Delay integration ship");
        ship.setStatus(ShipStatus.READY);
        ship.setTotalCapacity(20);
        ship.setTotalDecks(1);
        ship = ships.save(ship);

        Instant original = Instant.parse("2099-11-20T01:00:00Z");
        Cruise cruise = new Cruise();
        cruise.setCruiseCode("DELAY-" + UUID.randomUUID().toString().substring(0, 8));
        cruise.setCruiseName("Delay integration cruise");
        cruise.setCruiseType(CruiseCruiseType.ONE_DAY);
        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setShip(ship);
        cruise.setDeparturePort(port);
        cruise.setReturnPort(port);
        cruise.setDepartureDatetime(original);
        cruise.setReturnDatetime(original.plusSeconds(21600));
        cruise = cruises.save(cruise);
        UUID id = cruise.getCruiseId();
        UUID actor = UUID.randomUUID();

        Itinerary itinerary = new Itinerary();
        itinerary.setCruise(cruise);
        itinerary.setItineraryName("Materialized schedule");
        itinerary.setItineraryType(ItineraryType.ROUND_TRIP);
        itinerary.setStatus(ItineraryStatus.APPROVED);
        itinerary.setDeparturePortFacts(PortFacts.capture(port));
        var date = original.atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalDate();
        itinerary.setStartDate(date);
        itinerary.setEndDate(date);
        itinerary = itineraries.save(itinerary);

        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDate(date);
        day.setDayType(ItineraryDayType.PORT_DAY);
        day.setDayNumber(1);
        day.setShipTimeZoneId(PortTimeZoneHelper.SHIP_TIME_ZONE_ID);
        day = days.save(day);

        CruiseStop stop = new CruiseStop();
        stop.setItinerary(itinerary);
        stop.setArrivalItineraryDay(day);
        stop.setDepartureItineraryDay(day);
        stop.setPort(port);
        stop.setPortFacts(PortFacts.capture(port));
        stop.setStopOrder(1);
        stop.setCallType(CruiseStopCallType.NORMAL);
        stop.setStatus(CruiseStopStatus.PLANNED);
        stop.setPlannedArrival(original.plusSeconds(14400));
        stop.setPlannedDeparture(original.plusSeconds(21600));
        Instant originalAllAboard = original.plusSeconds(21000);
        stop.setAllAboardTime(originalAllAboard);
        UUID stopId = stops.save(stop).getCruiseStopId();

        var ready = new CruiseReadinessResponse();
        ready.setReady(true);
        doReturn(ready).when(cruiseService).validateDepartureReadiness(id);
        var pass = new Check(port.getPortId(), "DEPARTURE", "PASS", "MATCHED", null, null,
                port.getTimeZoneId(), null, null, null, null, null, original, List.of());
        when(windows.evaluate(any(), any(), any(), any())).thenReturn(List.of(pass));
        when(windows.departureMovement(any(), any(), any())).thenReturn(List.of(pass));

        var first = new CruiseDelayRequest(30, "Port coordination", "PORT_OPERATION", UUID.randomUUID(), cruise.getRevision());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> service.confirm(id, first, actor));
            var two = executor.submit(() -> service.confirm(id, first, actor));
            assertEquals(one.get(30, TimeUnit.SECONDS).eventId(), two.get(30, TimeUnit.SECONDS).eventId());
        }
        assertEquals(originalAllAboard.plusSeconds(1800), stops.findById(stopId).orElseThrow().getAllAboardTime());
        assertEquals(1, service.history(id).events().size());
        var second = new CruiseDelayRequest(90, "Weather", "WEATHER_CONDITION", UUID.randomUUID(),
                cruises.findById(id).orElseThrow().getRevision());
        service.confirm(id, second, actor);
        var history = service.history(id);
        assertEquals(original, history.originalDeparture());
        assertEquals(original.plusSeconds(7200), history.expectedDeparture());
        assertEquals(120, history.totalDelayMinutes());
        assertNull(history.actualDeparture());
        assertEquals(2, history.events().size());
        assertEquals(originalAllAboard.plusSeconds(7200), stops.findById(stopId).orElseThrow().getAllAboardTime());
        assertEquals("STALE_CRUISE_REVISION", assertThrows(BusinessException.class,
                () -> service.confirm(id, new CruiseDelayRequest(30, first.reason(), first.category(),
                        UUID.randomUUID(), first.expectedRevision()), actor)).getCode());

        var third = new CruiseDelayRequest(30, "Test rollback", "OTHER", UUID.randomUUID(),
                cruises.findById(id).orElseThrow().getRevision());
        doThrow(new IllegalStateException("audit persistence failed")).when(audit).recordEvent(
                any(), eq("CRUISE_PRE_DEPARTURE_DELAY"), endsWith(third.requestId().toString()),
                eq(actor), any(), any(), any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.confirm(id, third, actor));
        assertEquals(history.expectedDeparture(), service.history(id).expectedDeparture());
        assertEquals(2, service.history(id).events().size());
        assertEquals(originalAllAboard.plusSeconds(7200), stops.findById(stopId).orElseThrow().getAllAboardTime());
    }
}
