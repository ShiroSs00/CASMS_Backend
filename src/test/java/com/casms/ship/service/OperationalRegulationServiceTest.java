package com.casms.ship.service;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalRegulationServiceTest {
 @Mock OperationalRegulationRepository regulationRepository;
 @Mock ShipRegulationRepository shipRegulationRepository;
 @Mock CruiseRegulationRepository cruiseRegulationRepository;
 @Mock BusinessCodeGenerator businessCodeGenerator;
 @InjectMocks OperationalRegulationService service;

 @Test void listRegulations_returnsMappedList() {
  OperationalRegulation r=new OperationalRegulation(); r.setRegulationId(UUID.randomUUID());
  r.setRegulationCode("REG-01"); r.setName("Safety rule");
  when(regulationRepository.findAll()).thenReturn(List.of(r));
  var result=service.listRegulations();
  assertEquals(1,result.size()); assertEquals("REG-01",result.get(0).getRegulationCode());
 }
 @Test void detail_missingRegulation_throws() {
  UUID id=UUID.randomUUID(); when(regulationRepository.findById(id)).thenReturn(Optional.empty());
  assertThrows(EntityNotFoundException.class,()->service.getRegulationDetail(id));
 }
 @Test void effectiveRegulations_nullTime_throws() {
  assertThrows(IllegalArgumentException.class,()->service.getEffectiveRegulations((Instant)null));
  verify(regulationRepository,never()).findAll();
 }
 @Test void effectiveRegulations_filtersInactiveAndFuture() {
  Instant now=Instant.parse("2026-01-10T00:00:00Z");
  OperationalRegulation active=new OperationalRegulation();
  active.setRegulationId(UUID.randomUUID()); active.setRegulationCode("ACTIVE"); active.setName("Effective");
  active.setStatus(RegulationStatus.ACTIVE); active.setEffectiveFrom(now.minusSeconds(10)); active.setEffectiveTo(now.plusSeconds(10));
  OperationalRegulation future=new OperationalRegulation();
  future.setStatus(RegulationStatus.ACTIVE); future.setEffectiveFrom(now.plusSeconds(10));
  OperationalRegulation inactive=new OperationalRegulation();
  inactive.setStatus(RegulationStatus.INACTIVE); inactive.setEffectiveFrom(now.minusSeconds(10));
  when(regulationRepository.findAll()).thenReturn(List.of(active,future,inactive));
  var result=service.getEffectiveRegulations(now);
  assertEquals(1,result.size()); assertEquals("ACTIVE",result.get(0).getRegulationCode());
 }
 @Test void deleteAssignedRegulation_throws() {
  UUID id=UUID.randomUUID(); OperationalRegulation r=new OperationalRegulation();
  when(regulationRepository.findById(id)).thenReturn(Optional.of(r));
  when(shipRegulationRepository.existsByRegulation_RegulationId(id)).thenReturn(true);
  when(cruiseRegulationRepository.existsByRegulation_RegulationId(id)).thenReturn(false);
  assertThrows(IllegalStateException.class,()->service.deleteUnusedRegulation(id));
  verify(regulationRepository,never()).delete(r);
 }
 @Test void deactivateNonActiveRegulation_throws() {
  UUID id=UUID.randomUUID(); OperationalRegulation r=new OperationalRegulation(); r.setStatus(RegulationStatus.DRAFT);
  when(regulationRepository.findById(id)).thenReturn(Optional.of(r));
  assertThrows(IllegalStateException.class,()->service.deactivateRegulation(id));
  verify(regulationRepository,never()).save(r);
 }
}
