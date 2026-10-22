package com.casms.ship.service;
import com.casms.identity.repository.UserRepository;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.entity.OperationalIssue;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalIssueServiceTest {
 @Mock OperationalIssueRepository issueRepository;
 @Mock ShipRepository shipRepository;
 @Mock CruiseRepository cruiseRepository;
 @Mock CabinRepository cabinRepository;
 @Mock UserRepository userRepository;
 @Mock BusinessCodeGenerator businessCodeGenerator;
 @InjectMocks OperationalIssueService service;

 @Test void detail_missingIssue_throws() {
  UUID id=UUID.randomUUID(); when(issueRepository.findById(id)).thenReturn(Optional.empty());
  assertThrows(IllegalArgumentException.class,()->service.detail(id));
 }
 @Test void startHandling_nonOpenIssue_throws() {
  UUID id=UUID.randomUUID(); OperationalIssue issue=new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.IN_PROGRESS);
  when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
  assertThrows(IllegalStateException.class,()->service.startHandling(id));
  verify(issueRepository,never()).save(issue);
 }
 @Test void startHandling_unassignedIssue_throws() {
  UUID id=UUID.randomUUID(); OperationalIssue issue=new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.OPEN); issue.setAssignedTo(null);
  when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
  assertThrows(IllegalStateException.class,()->service.startHandling(id));
  verify(issueRepository,never()).save(issue);
 }
 @Test void close_nonResolvedIssue_throws() {
  UUID id=UUID.randomUUID(); OperationalIssue issue=new OperationalIssue(); issue.setStatus(OperationalIssueStatus.OPEN);
  when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
  assertThrows(IllegalStateException.class,()->service.close(id));
 }
 @Test void verifyResolution_withoutResolution_throws() {
  UUID id=UUID.randomUUID(); OperationalIssue issue=new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.IN_PROGRESS); issue.setResolution(null);
  when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
  assertThrows(IllegalStateException.class,()->service.verifyResolution(id,UUID.randomUUID()));
  verifyNoInteractions(userRepository);
 }
 @Test void reopen_openIssue_throws() {
  UUID id=UUID.randomUUID(); OperationalIssue issue=new OperationalIssue(); issue.setStatus(OperationalIssueStatus.OPEN);
  when(issueRepository.findById(id)).thenReturn(Optional.of(issue));
  assertThrows(IllegalStateException.class,()->service.reopen(id));
 }
 @Test void validateIssue_missingRequiredFields_throws() {
  UUID id=UUID.randomUUID(); when(issueRepository.findById(id)).thenReturn(Optional.of(new OperationalIssue()));
  assertThrows(IllegalStateException.class,()->service.validateIssue(id));
 }

 @Test void assignHandler_ineligibleUser_throwsHandlerNotEligible() {
  UUID issueId = UUID.randomUUID();
  UUID handlerId = UUID.randomUUID();
  OperationalIssue issue = new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.OPEN);

  com.casms.identity.entity.User passengerUser = new com.casms.identity.entity.User();
  passengerUser.setUserId(handlerId);
  passengerUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
  passengerUser.addRole(com.casms.identity.enums.Role.PASSENGER);

  when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
  when(userRepository.findById(handlerId)).thenReturn(Optional.of(passengerUser));

  com.casms.ship.dto.request.OperationalIssueAssignRequest req = new com.casms.ship.dto.request.OperationalIssueAssignRequest();
  req.setAssignedToId(handlerId);

  com.casms.common.exception.BusinessException ex = assertThrows(
          com.casms.common.exception.BusinessException.class,
          () -> service.assignHandler(issueId, req)
  );
  assertEquals("HANDLER_NOT_ELIGIBLE", ex.getCode());
 }

 @Test void assignHandler_inactiveUser_throwsHandlerNotActive() {
  UUID issueId = UUID.randomUUID();
  UUID handlerId = UUID.randomUUID();
  OperationalIssue issue = new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.OPEN);

  com.casms.identity.entity.User inactiveUser = new com.casms.identity.entity.User();
  inactiveUser.setUserId(handlerId);
  inactiveUser.setStatus(com.casms.identity.enums.UserStatus.DISABLED);

  when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
  when(userRepository.findById(handlerId)).thenReturn(Optional.of(inactiveUser));

  com.casms.ship.dto.request.OperationalIssueAssignRequest req = new com.casms.ship.dto.request.OperationalIssueAssignRequest();
  req.setAssignedToId(handlerId);

  com.casms.common.exception.BusinessException ex = assertThrows(
          com.casms.common.exception.BusinessException.class,
          () -> service.assignHandler(issueId, req)
  );
  assertEquals("HANDLER_NOT_ACTIVE", ex.getCode());
 }

 @Test void assignHandler_eligibleStaff_succeeds() {
  UUID issueId = UUID.randomUUID();
  UUID handlerId = UUID.randomUUID();
  OperationalIssue issue = new OperationalIssue();
  issue.setStatus(OperationalIssueStatus.OPEN);

  com.casms.identity.entity.User opsUser = new com.casms.identity.entity.User();
  opsUser.setUserId(handlerId);
  opsUser.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
  opsUser.addRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER);

  when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
  when(userRepository.findById(handlerId)).thenReturn(Optional.of(opsUser));
  when(issueRepository.save(any(OperationalIssue.class))).thenAnswer(i -> i.getArgument(0));

  com.casms.ship.dto.request.OperationalIssueAssignRequest req = new com.casms.ship.dto.request.OperationalIssueAssignRequest();
  req.setAssignedToId(handlerId);

  var res = service.assignHandler(issueId, req);
  assertNotNull(res);
  assertEquals(handlerId, res.getAssignedToId());
  assertEquals(opsUser, issue.getAssignedTo());
 }
}
