package com.casms.ship.service;

import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.*;
import com.casms.audit.AuditService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortWindowEvaluatorTest {
    private final UUID id=UUID.randomUUID();
    private final String zone="Asia/Singapore";
    private final LocalDate day=LocalDate.of(2026,11,2);
    private PortWindowService directory;
    private PortWindowEvaluator evaluator;
    private Port port;
    @BeforeEach void setup(){
        directory=mock(PortWindowService.class);port=new Port();port.setPortId(id);port.setTimeZoneId(zone);port.setStatus(PortStatus.ACTIVE);
        when(directory.lock(id)).thenReturn(port);
        evaluator=new PortWindowEvaluator(directory,mock(PortRepository.class),mock(CruiseStopRepository.class),mock(ItineraryRepository.class),mock(AuditService.class));
    }
    private Instant at(String time){return LocalDateTime.of(day,LocalTime.parse(time)).atZone(ZoneId.of(zone)).toInstant();}
    private Window window(String type,String start,String end,String verification,String lifecycle,Boolean overnight,Integer max){return new Window(UUID.randomUUID(),type,day.minusDays(1),day.plusDays(5),Set.of(DayOfWeek.values()),LocalTime.parse(start),LocalTime.parse(end),"PORT_AUTHORITY","Authority document",day.minusDays(1),overnight,max,verification,lifecycle,UUID.randomUUID(),Instant.now(),zone,2L,Instant.now(),Instant.now());}
    private void data(List<Window> windows,List<Closure> closures){when(directory.read(id)).thenReturn(new Directory(id,zone,windows,closures));}
    @ParameterizedTest @CsvSource({"08:00,16:00,PASS,PASS","10:00,16:00,BLOCK,PASS","08:00,18:00,PASS,BLOCK"})
    void halfOpenIndependentMovements(String eta,String etd,String arr,String dep){
        data(List.of(window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",null,null),window("DEPARTURE","16:00","18:00","VERIFIED","ACTIVE",null,null)),List.of());
        var result=evaluator.evaluate(id,zone,at(eta),at(etd));assertEquals(arr,result.get(0).status());assertEquals(dep,result.get(1).status());
    }
    @Test void usesSecondWindowInUnion(){data(List.of(window("ARRIVAL","06:00","07:00","VERIFIED","ACTIVE",null,null),window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",null,null),window("DEPARTURE","16:00","18:00","VERIFIED","ACTIVE",null,null)),List.of());assertTrue(evaluator.evaluate(id,zone,at("08:00"),at("16:00")).stream().allMatch(c->"PASS".equals(c.status())));}
    @ParameterizedTest @CsvSource({"UNVERIFIED,ACTIVE,WINDOW_NOT_VERIFIED","VERIFIED,INACTIVE,WINDOW_NOT_ACTIVE"})
    void neverPassesUnverifiedOrInactive(String verification,String lifecycle,String code){data(List.of(window("ARRIVAL","08:00","10:00",verification,lifecycle,null,null)),List.of());assertEquals(code,evaluator.evaluate(id,zone,at("08:00"),at("16:00")).getFirst().code());}
    @Test void closureOverridesWindows(){var closure=new Closure(UUID.randomUUID(),"CLOSURE",day,day,"Closed for safety","PORT_AGENT","Agent notice",day,"VERIFIED","ACTIVE",UUID.randomUUID(),Instant.now(),zone,4L,Instant.now(),Instant.now());data(List.of(window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",null,null)),List.of(closure));assertTrue(evaluator.evaluate(id,zone,at("08:00"),at("16:00")).stream().allMatch(c->"CLOSED".equals(c.code())));}
    @Test void missingWindowsFailClosed(){data(List.of(),List.of());assertTrue(evaluator.evaluate(id,zone,at("08:00"),at("16:00")).stream().allMatch(c->"WINDOW_NOT_CONFIGURED".equals(c.code())));}
    @Test void overnightNeedsExplicitSource(){data(List.of(window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",null,null),window("DEPARTURE","16:00","18:00","VERIFIED","ACTIVE",null,null)),List.of());assertEquals("OVERNIGHT_NOT_ALLOWED",evaluator.evaluate(id,zone,at("08:00"),at("16:00").plus(Duration.ofDays(1))).getFirst().code());}
    @Test void sourcedOvernightAndMaxStay(){data(List.of(window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",true,2000),window("DEPARTURE","16:00","18:00","VERIFIED","ACTIVE",null,null)),List.of());assertTrue(evaluator.evaluate(id,zone,at("08:00"),at("16:00").plus(Duration.ofDays(1))).stream().allMatch(c->"PASS".equals(c.status())));data(List.of(window("ARRIVAL","08:00","10:00","VERIFIED","ACTIVE",true,60),window("DEPARTURE","16:00","18:00","VERIFIED","ACTIVE",null,null)),List.of());assertEquals("MAX_STAY_EXCEEDED",evaluator.evaluate(id,zone,at("08:00"),at("16:00").plus(Duration.ofDays(1))).getFirst().code());}
    @Test void timezoneChangeRequiresReview(){port.setTimeZoneId("Asia/Tokyo");assertEquals("PORT_TIMEZONE_CHANGED",evaluator.evaluate(id,zone,at("08:00"),at("16:00")).getFirst().code());}
    @Test void legacySnapshotCannotGrantPass(){assertEquals("PORT_SNAPSHOT_UNAVAILABLE",evaluator.evaluate(id,null,at("08:00"),at("16:00")).getFirst().code());}
}
