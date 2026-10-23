package com.casms.ship.service;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.ShipCreateRequest;
import com.casms.ship.dto.request.ShipUpdateRequest;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipServiceTest {
 @Mock ShipRepository shipRepository;
 @Mock CruiseRepository cruiseRepository;
 @Mock DeckRepository deckRepository;
 @Mock CabinRepository cabinRepository;
 @Mock ShipModelRepository shipModelRepository;
 @Mock LocationDeviceRepository locationDeviceRepository;
 @Mock BusinessCodeGenerator businessCodeGenerator;
 @InjectMocks ShipService service;

 @Test void create_savesAndReturnsShip() {
  ShipCreateRequest req=mock(ShipCreateRequest.class);
  when(businessCodeGenerator.next("SH")).thenReturn("SH-0001");
  when(req.getShipName()).thenReturn("Test Ship");
  when(req.getStatus()).thenReturn(ShipStatus.values()[0]);
  when(req.getTotalCapacity()).thenReturn(100);
  when(req.getTotalDecks()).thenReturn(5);
  when(req.getLengthMeters()).thenReturn(null);
  when(req.getBeamMeters()).thenReturn(null);
  when(shipRepository.save(any(Ship.class))).thenAnswer(i -> { Ship s=i.getArgument(0); s.setShipId(UUID.randomUUID()); return s; });
  var res=service.create(req);
  assertEquals("SH-0001",res.getShipCode());
  assertEquals("Test Ship",res.getShipName());
  verify(shipRepository).save(any(Ship.class));
 }
 @Test void list_mapsPage() {
  Ship s=new Ship(); s.setShipCode("SH-01"); s.setShipName("Test");
  when(shipRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s)));
  assertEquals("SH-01",service.list(PageRequest.of(0,10)).getContent().get(0).getShipCode());
 }
 @Test void detail_missingShip_throws() {
  UUID id=UUID.randomUUID(); when(shipRepository.findById(id)).thenReturn(Optional.empty());
  assertThrows(ResourceNotFoundException.class,()->service.detail(id));
 }
 @Test void update_updatesNonNullFields() {
  UUID id=UUID.randomUUID(); Ship s=new Ship(); s.setShipId(id); s.setShipName("Old");
  when(shipRepository.findById(id)).thenReturn(Optional.of(s));
  when(shipRepository.save(any(Ship.class))).thenAnswer(i->i.getArgument(0));
  ShipUpdateRequest req=mock(ShipUpdateRequest.class);
  when(req.getShipName()).thenReturn("New"); when(req.getTotalCapacity()).thenReturn(200);
  when(req.getLengthMeters()).thenReturn(null); when(req.getBeamMeters()).thenReturn(null);
  when(req.getTotalDecks()).thenReturn(null); when(req.getCommissionDate()).thenReturn(null);
  assertEquals("New",service.update(id,req).getShipName());
  verify(shipRepository).save(s);
 }
 @Test void deleteUnused_whenUsed_throws() {
  UUID id=UUID.randomUUID(); Ship s=new Ship();
  when(shipRepository.findById(id)).thenReturn(Optional.of(s));
  when(cruiseRepository.existsByShip(s)).thenReturn(true);
  assertThrows(BusinessException.class,()->service.deleteUnused(id));
  verify(shipRepository,never()).delete(any(Ship.class));
 }
 @Test void scheduleAvailability_rejectsInvalidRange() {
  UUID id=UUID.randomUUID(); when(shipRepository.findById(id)).thenReturn(Optional.of(new Ship()));
  assertThrows(BusinessException.class,()->service.checkScheduleAvailability(id,
   Instant.parse("2026-02-02T00:00:00Z"),Instant.parse("2026-02-01T00:00:00Z")));
 }
}
