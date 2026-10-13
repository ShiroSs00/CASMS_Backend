package com.casms.ship.service;
import com.casms.identity.repository.UserRepository;
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
}
