package com.casms.ship.service;
import com.casms.ship.entity.*;
import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class ItineraryCalendarTest {
 @Test void calendarUsesShipDatesAcrossUtcMidnightAndIncludesReturnDay(){
  Cruise c=new Cruise();c.setDepartureDatetime(Instant.parse("2026-11-01T18:00:00Z"));c.setReturnDatetime(Instant.parse("2026-11-03T18:00:00Z"));
  assertEquals(LocalDate.parse("2026-11-02"),ItineraryCalendar.start(c));assertEquals(LocalDate.parse("2026-11-04"),ItineraryCalendar.end(c));
  Itinerary i=new Itinerary();i.setCruise(c);
  assertEquals(LocalDate.parse("2026-11-02"),ItineraryCalendar.day(i,1,null));assertEquals(LocalDate.parse("2026-11-04"),ItineraryCalendar.day(i,3,null));
  assertThrows(BusinessException.class,()->ItineraryCalendar.day(i,1,LocalDate.parse("2026-11-03")));
  assertThrows(BusinessException.class,()->ItineraryCalendar.day(i,4,null));
 }
 @Test void sameDayCruiseIsValidButAnInnerSubsetIsRejected(){
  Cruise c=new Cruise();c.setDepartureDatetime(Instant.parse("2026-11-01T01:00:00Z"));c.setReturnDatetime(Instant.parse("2026-11-01T11:00:00Z"));
  assertDoesNotThrow(()->ItineraryCalendar.validate(c,LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-01")));
  assertThrows(BusinessException.class,()->ItineraryCalendar.validate(c,LocalDate.parse("2026-10-31"),LocalDate.parse("2026-11-01")));
 }
}
