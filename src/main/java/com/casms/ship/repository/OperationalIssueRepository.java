
package com.casms.ship.repository;

import com.casms.ship.entity.OperationalIssue;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.enums.OperationalIssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OperationalIssueRepository
        extends JpaRepository<OperationalIssue, UUID> {

    boolean existsByIssueCode(String issueCode);

    List<OperationalIssue> findAllByOrderByReportedAtDesc();

    List<OperationalIssue> findByStatusOrderByReportedAtDesc(
            OperationalIssueStatus status);

    List<OperationalIssue> findByIssueTypeOrderByReportedAtDesc(
            OperationalIssueType issueType);

    List<OperationalIssue> findByShip_ShipIdOrderByReportedAtDesc(
            UUID shipId);

    List<OperationalIssue> findByCruise_CruiseIdOrderByReportedAtDesc(
            UUID cruiseId);

    List<OperationalIssue> findByStatusAndShip_ShipIdOrderByReportedAtDesc(
            OperationalIssueStatus status, UUID shipId);

    List<OperationalIssue> findByAssignedTo_UserIdOrderByReportedAtDesc(
            UUID userId);
}
