package com.casms.ship.service;

import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseRegulation;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.repository.CruiseRegulationRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.OperationalRegulationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CruiseRegulationServiceTest {
 @Mock CruiseRegulationRepository cruiseRegulationRepository;
 @Mock CruiseRepository cruiseRepository;
 @Mock OperationalRegulationRepository operationalRegulationRepository;
 @InjectMocks CruiseRegulationService service;

 @Test
 void assign_nullRequest_throws() {
  assertThrows(IllegalArgumentException.class, () -> service.assign(null));
  verifyNoInteractions(cruiseRepository, operationalRegulationRepository);
 }

 @Test
 void assign_missingCruise_throws() {
  UUID cruiseId = UUID.randomUUID();
  UUID regulationId = UUID.randomUUID();
  CruiseRegulationRequest request = mock(CruiseRegulationRequest.class);
  when(request.getCruiseId()).thenReturn(cruiseId);
  when(request.getRegulationId()).thenReturn(regulationId);
  when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.empty());

  assertThrows(EntityNotFoundException.class, () -> service.assign(request));
  verify(cruiseRegulationRepository, never()).save(any());
 }

 @Test
 void assign_missingRegulation_throws() {
  UUID cruiseId = UUID.randomUUID();
  UUID regulationId = UUID.randomUUID();
  CruiseRegulationRequest request = mock(CruiseRegulationRequest.class);
  when(request.getCruiseId()).thenReturn(cruiseId);
  when(request.getRegulationId()).thenReturn(regulationId);
  when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(new Cruise()));
  when(operationalRegulationRepository.findById(regulationId)).thenReturn(Optional.empty());

  assertThrows(EntityNotFoundException.class, () -> service.assign(request));
  verify(cruiseRegulationRepository, never()).save(any());
 }

 @Test
 void assign_globalRegulation_rejected() {
  UUID cruiseId = UUID.randomUUID();
  UUID regulationId = UUID.randomUUID();
  CruiseRegulationRequest request = mock(CruiseRegulationRequest.class);
  when(request.getCruiseId()).thenReturn(cruiseId);
  when(request.getRegulationId()).thenReturn(regulationId);
  when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(new Cruise()));
  OperationalRegulation regulation = new OperationalRegulation();
  regulation.setScope(RegulationScope.GLOBAL);
  when(operationalRegulationRepository.findById(regulationId)).thenReturn(Optional.of(regulation));

  assertThrows(IllegalArgumentException.class, () -> service.assign(request));
  verify(cruiseRegulationRepository, never()).save(any());
 }

 @Test
 void remove_nullIds_throws() {
  assertThrows(IllegalArgumentException.class, () -> service.remove(null, UUID.randomUUID()));
  assertThrows(IllegalArgumentException.class, () -> service.remove(UUID.randomUUID(), null));
  verifyNoInteractions(cruiseRegulationRepository);
 }

 @Test
 void validateApplicableRegulation_null_throws() {
  assertThrows(IllegalArgumentException.class, () -> service.validateApplicableRegulation(null));
 }

 @Test
 void checkRegulationConflict_nullIds_throws() {
  assertThrows(IllegalArgumentException.class, () -> service.checkRegulationConflict(null, UUID.randomUUID()));
  assertThrows(IllegalArgumentException.class, () -> service.checkRegulationConflict(UUID.randomUUID(), null));
 }
}
