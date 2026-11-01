package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.repository.*;
import com.casms.activity.repository.SupplierRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceScheduleEditingTest {
 private final ServiceRepository services=mock(ServiceRepository.class);
 private final ServiceScheduleRepository schedules=mock(ServiceScheduleRepository.class);
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final ServiceScheduleManagementService subject=new ServiceScheduleManagementService(services,schedules,mock(CruiseRepository.class),mock(CruiseStopRepository.class),mock(SupplierRepository.class),jdbc,mock(ShipServiceAvailability.class));
 private final UUID id=UUID.randomUUID(),cruiseId=UUID.randomUUID();
 private ServiceSchedule existing(){
  Cruise cruise=new Cruise();cruise.setCruiseId(cruiseId);
  ServiceSchedule window=new ServiceSchedule();window.setCruise(cruise);
  when(schedules.lockById(id)).thenReturn(Optional.of(window));return window;
 }
 @Test void linkedRequestsBlockBothEditAndDeleteBeforeAnyMutation(){
  existing();when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id))).thenReturn(true);
  assertThrows(BusinessException.class,()->subject.remove(id,cruiseId));
  assertThrows(BusinessException.class,()->subject.update(UUID.randomUUID(),cruiseId,null,null,Instant.now(),Instant.now().plusSeconds(60),"Dining",5,id));
  verify(schedules,never()).delete(any());verify(schedules,never()).save(any());verifyNoInteractions(services);
 }
 @Test void foreignCruiseCannotDeleteWindow(){
  existing();assertThrows(BusinessException.class,()->subject.remove(id,UUID.randomUUID()));
  verifyNoInteractions(jdbc,services);verify(schedules,never()).delete(any());
 }
 @Test void unusedWindowCanBeDeletedInItsCruise(){
  var window=existing();when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id))).thenReturn(false);
  subject.remove(id,cruiseId);verify(schedules).delete(window);
 }
}
