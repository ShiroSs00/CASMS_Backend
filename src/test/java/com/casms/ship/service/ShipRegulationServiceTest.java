package com.casms.ship.service;
import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.entity.*;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipRegulationServiceTest {
 @Mock ShipRegulationRepository shipRegulationRepository;
 @Mock ShipRepository shipRepository;
 @Mock OperationalRegulationRepository operationalRegulationRepository;
 @InjectMocks ShipRegulationService service;

 @Test void assign_nullRequest_throws() {
  assertThrows(IllegalArgumentException.class,()->service.assign(null));
  verifyNoInteractions(shipRepository,operationalRegulationRepository);
 }
 @Test void assign_missingShip_throws() {
  UUID sid=UUID.randomUUID(), rid=UUID.randomUUID();
  ShipRegulationRequest req=mock(ShipRegulationRequest.class);
  when(req.getShipId()).thenReturn(sid); when(req.getRegulationId()).thenReturn(rid);
  when(shipRepository.findById(sid)).thenReturn(Optional.empty());
  assertThrows(EntityNotFoundException.class,()->service.assign(req));
 }
 @Test void assign_globalRegulation_rejected() {
  UUID sid=UUID.randomUUID(), rid=UUID.randomUUID();
  ShipRegulationRequest req=mock(ShipRegulationRequest.class);
  when(req.getShipId()).thenReturn(sid); when(req.getRegulationId()).thenReturn(rid);
  Ship ship=new Ship(); ship.setShipId(sid);
  OperationalRegulation reg=new OperationalRegulation(); reg.setScope(RegulationScope.GLOBAL);
  when(shipRepository.findById(sid)).thenReturn(Optional.of(ship));
  when(operationalRegulationRepository.findById(rid)).thenReturn(Optional.of(reg));
  assertThrows(IllegalArgumentException.class,()->service.assign(req));
  verify(shipRegulationRepository,never()).save(any());
 }
 @Test void remove_existingRelation_deletesIt() {
  when(shipRegulationRepository.findById(any(ShipRegulation.ShipRegulationId.class)))
    .thenReturn(Optional.of(mock(ShipRegulation.class)));
  ShipRegulation relation=shipRegulationRepository.findById(new ShipRegulation.ShipRegulationId()).orElseThrow();
  service.remove(UUID.randomUUID(),UUID.randomUUID());
  verify(shipRegulationRepository).delete(relation);
 }
 @Test void remove_nullId_throws() {
  assertThrows(IllegalArgumentException.class,()->service.remove(null,UUID.randomUUID()));
 }
 @Test void validateApplicableRegulation_null_throws() {
  assertThrows(IllegalArgumentException.class,()->service.validateApplicableRegulation(null));
 }
}
