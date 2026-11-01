package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.*;
import com.casms.ship.entity.*;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PreparationServiceAuthorizationTest {
 @Mock CruiseRepository cruises;
 @Mock CruisePreparationSectionRepository sections;
 @Mock CruiseStaffRepository staff;
 @Mock DeckRepository decks;
 @Mock CabinRepository cabins;
 @Mock com.casms.activity.repository.ShoreExcursionRepository tours;
 @Mock com.casms.activity.repository.OnboardActivityRepository activities;
 @Mock com.casms.servicepackage.repository.ServiceScheduleRepository schedules;
 @Mock com.casms.identity.repository.UserRepository users;
 @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Spy com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
 @InjectMocks CruisePreparationService service;
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private CasmsPrincipal actor(UUID id,boolean crm,boolean ops,boolean grant){
  var p=mock(CasmsPrincipal.class);lenient().when(p.getUserId()).thenReturn(id);
  lenient().when(p.hasRole(Role.CRUISE_MANAGER)).thenReturn(crm);lenient().when(p.hasRole(Role.OPERATIONS_MANAGER)).thenReturn(ops);
  lenient().when(p.hasAuthority(anyString())).thenReturn(grant);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));return p;
 }
 @Test void callerSuppliedRolesAndActorCannotBypassActualPrincipal(){
  UUID id=UUID.randomUUID();actor(id,false,true,true);
  var r=new CruisePreparationSubmitRequest();r.setExpectedRevision(0L);r.setEvidence("Caller evidence");
  assertEquals("PREPARATION_ACTION_FORBIDDEN",assertThrows(BusinessException.class,()->service.submitSection(UUID.randomUUID(),"SAFETY_CREW",r,id,Set.of("CRUISE_MANAGER","ADMIN"))).getCode());
  assertEquals("AUTH_REQUIRED",assertThrows(BusinessException.class,()->service.submitSection(UUID.randomUUID(),"SAFETY_CREW",r,UUID.randomUUID(),Set.of())).getCode());
  verifyNoInteractions(cruises,sections,jdbc);
 }
 @Test void crmRoleWithoutEffectiveGrantCannotSubmit(){
  UUID id=UUID.randomUUID();actor(id,true,false,false);
  assertEquals("PREPARATION_ACTION_FORBIDDEN",assertThrows(BusinessException.class,()->service.submitSection(UUID.randomUUID(),"TECHNICAL_NAUTICAL",new CruisePreparationSubmitRequest(),id,Set.of())).getCode());
  verifyNoInteractions(cruises,sections);
 }
 @Test void dualRoleSubmitterStillCannotConfirmOwnEvidence(){
  UUID id=UUID.randomUUID(),cruiseId=UUID.randomUUID();actor(id,true,true,true);
  var cruise=new Cruise();cruise.setCruiseId(cruiseId);
  var section=new CruisePreparationSection();section.setCruise(cruise);section.setStatus("SUBMITTED");section.setRevision(1L);section.setSubmittedByUserId(id);
  when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));when(sections.findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId,"HOTEL_CABIN")).thenReturn(Optional.of(section));
  var r=new CruisePreparationConfirmRequest();r.setExpectedRevision(1L);
  assertEquals("SELF_APPROVAL_FORBIDDEN",assertThrows(BusinessException.class,()->service.confirmSection(cruiseId,"HOTEL_CABIN",r,id,"spoofed")).getCode());
  verify(sections,never()).saveAndFlush(any());verifyNoInteractions(jdbc);
 }
 @Test void missingAssignmentNeverFallsBackToAnyCrm(){
  UUID id=UUID.randomUUID(),cruiseId=UUID.randomUUID();actor(id,true,false,true);
  var cruise=new Cruise();cruise.setCruiseId(cruiseId);when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
  assertEquals("PREPARATION_CRUISE_SCOPE_FORBIDDEN",assertThrows(BusinessException.class,()->service.submitSection(cruiseId,"HOTEL_CABIN",new CruisePreparationSubmitRequest(),id,Set.of())).getCode());verifyNoInteractions(sections,jdbc);
 }
 @Test void crmCannotBypassReadyControllerByCallingServiceWithOpsPermission(){
  UUID id=UUID.randomUUID();actor(id,true,false,true);
  var plans=new CruiseMainflowService(jdbc,mapper,mock(PortWindowEvaluator.class),mock(org.springframework.beans.factory.ObjectProvider.class));
  assertEquals("READY_ACTION_FORBIDDEN",assertThrows(BusinessException.class,()->plans.setReady(UUID.randomUUID(),new CruiseMainflowService.Action(1L,null,null),id)).getCode());
  verifyNoInteractions(jdbc);
 }
 @Test void materialChangeInvalidatesConfirmedEvidenceWithActorTimeRevisionAndAudit(){
  UUID actorId=UUID.randomUUID(),cruiseId=UUID.randomUUID();actor(actorId,false,true,true);
  var cruise=new Cruise();cruise.setCruiseId(cruiseId);
  var section=new CruisePreparationSection();section.setCruise(cruise);section.setSectionCode("SAFETY_CREW");section.setStatus("CONFIRMED");section.setRevision(3L);
  when(sections.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId)).thenReturn(List.of(section));
  when(sections.saveAndFlush(section)).thenAnswer(i->{section.setRevision(4L);return section;});
  service.invalidateSignoffs(cruiseId,"Crew assignment removed");
  assertEquals("RETURNED",section.getStatus());assertEquals(actorId,section.getReturnedByUserId());assertNotNull(section.getReturnedAt());assertEquals(4L,section.getRevision());
  assertTrue(section.getReturnReason().contains("Crew assignment removed"));
  verify(jdbc).update(anyString(),any(),eq(cruiseId),eq("SAFETY_CREW"),eq(4L),eq("INVALIDATE"),eq(actorId),eq("Crew assignment removed"),anyString());
 }
}
