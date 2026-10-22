package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.OperationalIssueAssignRequest;
import com.casms.ship.dto.request.OperationalIssueRequest;
import com.casms.ship.dto.request.OperationalIssueResolutionRequest;
import com.casms.ship.dto.request.OperationalIssueUpdateRequest;
import com.casms.ship.dto.response.OperationalIssueResponse;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.OperationalIssue;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.OperationalIssueSeverity;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.OperationalIssueRepository;
import com.casms.ship.repository.ShipRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OperationalIssueService {

    private final OperationalIssueRepository issueRepository;
    private final ShipRepository shipRepository;
    private final CruiseRepository cruiseRepository;
    private final CabinRepository cabinRepository;
    private final UserRepository userRepository;
    private final BusinessCodeGenerator businessCodeGenerator;

    // 1. Report
    public OperationalIssueResponse report(
            @Valid OperationalIssueRequest request,
            UUID reporterId) {

        if (request.getSeverity() == null) {
            request.setSeverity(OperationalIssueSeverity.MEDIUM);
        }

        User reporter = findUser(reporterId);
        Cabin cabin = findOptionalCabin(request.getCabinId());
        Cruise cruise = findOptionalCruise(request.getCruiseId());

        UUID effectiveShipId = request.getShipId();
        if (effectiveShipId == null) {
            if (cabin != null && cabin.getShip() != null) {
                effectiveShipId = cabin.getShip().getShipId();
            } else if (cruise != null && cruise.getShip() != null) {
                effectiveShipId = cruise.getShip().getShipId();
            } else {
                throw new IllegalArgumentException("Ship ID is required when cabin or cruise is not specified.");
            }
        }
        Ship ship = findShip(effectiveShipId);

        validateLocation(ship, cruise, cabin);

        OperationalIssue issue = new OperationalIssue();
        issue.setIssueCode(businessCodeGenerator.next("OI"));
        issue.setShip(ship);
        issue.setCruise(cruise);
        issue.setCabin(cabin);
        issue.setIssueType(request.getIssueType());
        issue.setSeverity(request.getSeverity());
        issue.setDescription(request.getDescription().trim());
        issue.setReportedBy(reporter);
        issue.setReportedAt(Instant.now());
        issue.setStatus(OperationalIssueStatus.OPEN);

        return toResponse(issueRepository.save(issue));
    }

    // 2. List, with optional filters
    @Transactional(readOnly = true)
    public List<OperationalIssueResponse> list(
            OperationalIssueStatus status,
            UUID shipId,
            UUID cruiseId) {

        List<OperationalIssue> issues;

        if (status != null && shipId != null) {
            issues = issueRepository.findByStatusAndShip_ShipIdOrderByReportedAtDesc(status, shipId);
        } else if (status != null && cruiseId != null) {
            issues = issueRepository.findByStatusAndCruise_CruiseIdOrderByReportedAtDesc(
                    status, cruiseId);
        } else if (status != null) {
            issues = issueRepository.findByStatusOrderByReportedAtDesc(status);
        } else if (shipId != null) {
            issues = issueRepository.findByShip_ShipIdOrderByReportedAtDesc(shipId);
        } else if (cruiseId != null) {
            issues = issueRepository.findByCruise_CruiseIdOrderByReportedAtDesc(cruiseId);
        } else {
            issues = issueRepository.findAllByOrderByReportedAtDesc();
        }

        return issues.stream()
                .map(this::toResponse)
                .toList();
    }

    // 3. Detail
    @Transactional(readOnly = true)
    public OperationalIssueResponse detail(UUID issueId) {
        return toResponse(findIssue(issueId));
    }

    // 4. Update an OPEN issue
    public OperationalIssueResponse updateOpen(
            UUID issueId,
            @Valid OperationalIssueUpdateRequest request) {

        OperationalIssue issue = findIssue(issueId);
        requireStatus(issue, OperationalIssueStatus.OPEN);

        if (request.getSeverity() != null) {
            issue.setSeverity(request.getSeverity());
        }

        issue.setDescription(request.getDescription().trim());

        return toResponse(issueRepository.save(issue));
    }

    // 5. Assign handler
    public OperationalIssueResponse assignHandler(
            UUID issueId,
            OperationalIssueAssignRequest request) {

        OperationalIssue issue = findIssue(issueId);

        if (issue.getStatus() != OperationalIssueStatus.OPEN
                && issue.getStatus() != OperationalIssueStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Handler can only be assigned to OPEN or IN_PROGRESS issues");
        }

        User handler = findUser(request.getAssignedToId());
        if (handler.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "HANDLER_NOT_ACTIVE",
                    "Handler must be an ACTIVE user"
            );
        }

        if (!com.casms.identity.service.IdentityService.isEligibleOperationalHandler(handler)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "HANDLER_NOT_ELIGIBLE",
                    "User is not eligible to handle operational issues"
            );
        }

        issue.setAssignedTo(handler);

        return toResponse(issueRepository.save(issue));
    }

    // 6. Start handling
    public OperationalIssueResponse startHandling(UUID issueId) {
        OperationalIssue issue = findIssue(issueId);
        requireStatus(issue, OperationalIssueStatus.OPEN);

        if (issue.getAssignedTo() == null) {
            throw new IllegalStateException(
                    "Assign a handler before starting the issue");
        }

        issue.setStatus(OperationalIssueStatus.IN_PROGRESS);
        return toResponse(issueRepository.save(issue));
    }

    // 7. Record resolution
    public OperationalIssueResponse recordResolution(
            UUID issueId,
            UUID resolverId,
            @Valid OperationalIssueResolutionRequest request) {

        OperationalIssue issue = findIssue(issueId);
        requireStatus(issue, OperationalIssueStatus.IN_PROGRESS);

        User resolver = findUser(resolverId);

        issue.setResolution(request.getResolution().trim());
        issue.setResolvedBy(resolver);
        issue.setResolvedAt(Instant.now());

        // Reset verification fields if resolution is updated
        issue.setVerifiedBy(null);
        issue.setVerifiedAt(null);

        return toResponse(issueRepository.save(issue));
    }

    // 8. Verify resolution
    public OperationalIssueResponse verifyResolution(
            UUID issueId,
            UUID verifierId) {

        OperationalIssue issue = findIssue(issueId);
        requireStatus(issue, OperationalIssueStatus.IN_PROGRESS);

        if (issue.getResolution() == null
                || issue.getResolution().isBlank()) {
            throw new IllegalStateException(
                    "Record a resolution before verifying the issue");
        }

        User verifier = findUser(verifierId);

        issue.setVerifiedBy(verifier);
        issue.setVerifiedAt(Instant.now());
        issue.setStatus(OperationalIssueStatus.RESOLVED);

        return toResponse(issueRepository.save(issue));
    }

    // 9. Close
    public OperationalIssueResponse close(UUID issueId) {
        OperationalIssue issue = findIssue(issueId);
        requireStatus(issue, OperationalIssueStatus.RESOLVED);

        issue.setStatus(OperationalIssueStatus.CLOSED);
        return toResponse(issueRepository.save(issue));
    }

    // 10. Reopen
    public OperationalIssueResponse reopen(UUID issueId) {
        OperationalIssue issue = findIssue(issueId);

        if (issue.getStatus() != OperationalIssueStatus.RESOLVED
                && issue.getStatus() != OperationalIssueStatus.CLOSED) {
            throw new IllegalStateException(
                    "Only RESOLVED or CLOSED issues can be reopened");
        }

        issue.setStatus(OperationalIssueStatus.OPEN);
        issue.setResolvedAt(null);
        issue.setResolvedBy(null);
        issue.setResolution(null);
        issue.setVerifiedBy(null);
        issue.setVerifiedAt(null);

        return toResponse(issueRepository.save(issue));
    }

    // 11. Validate issue data and workflow consistency
    @Transactional(readOnly = true)
    public void validateIssue(UUID issueId) {
        OperationalIssue issue = findIssue(issueId);

        if (issue.getIssueCode() == null || issue.getIssueCode().isBlank()) {
            throw new IllegalStateException("Issue code is required");
        }

        if (issue.getDescription() == null || issue.getDescription().isBlank()) {
            throw new IllegalStateException("Description is required");
        }

        validateLocation(
                issue.getShip(), issue.getCruise(), issue.getCabin());

        if (issue.getStatus() == OperationalIssueStatus.RESOLVED
                || issue.getStatus() == OperationalIssueStatus.CLOSED) {

            if (issue.getResolution() == null
                    || issue.getResolution().isBlank()
                    || issue.getResolvedAt() == null
                    || issue.getResolvedBy() == null
                    || issue.getVerifiedAt() == null
                    || issue.getVerifiedBy() == null) {
                throw new IllegalStateException(
                        "Resolved or closed issue must have a recorded and verified resolution");
            }
        }
    }

    // ---------- Private helpers ----------

    private OperationalIssue findIssue(UUID issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operational issue not found: " + issueId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId));
    }

    private Ship findShip(UUID shipId) {
        return shipRepository.findById(shipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ship not found: " + shipId));
    }

    private Cruise findOptionalCruise(UUID cruiseId) {
        if (cruiseId == null) {
            return null;
        }

        return cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cruise not found: " + cruiseId));
    }

    private Cabin findOptionalCabin(UUID cabinId) {
        if (cabinId == null) {
            return null;
        }

        return cabinRepository.findById(cabinId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cabin not found: " + cabinId));
    }

    private void validateLocation(
            Ship ship, Cruise cruise, Cabin cabin) {

        if (cruise != null && cruise.getShip() != null
                && !ship.getShipId().equals(
                cruise.getShip().getShipId())) {
            throw new IllegalArgumentException(
                    "Cruise does not belong to the specified ship");
        }

        if (cabin != null && cabin.getShip() != null
                && !ship.getShipId().equals(
                cabin.getShip().getShipId())) {
            throw new IllegalArgumentException(
                    "Cabin does not belong to the specified ship");
        }
    }

    private void requireStatus(
            OperationalIssue issue,
            OperationalIssueStatus expectedStatus) {

        if (issue.getStatus() != expectedStatus) {
            throw new IllegalStateException(
                    "Expected status " + expectedStatus
                            + " but current status is " + issue.getStatus());
        }
    }

    private OperationalIssueResponse toResponse(
            OperationalIssue issue) {

        return OperationalIssueResponse.builder()
                .issueId(issue.getIssueId())
                .issueCode(issue.getIssueCode())
                .cruiseId(issue.getCruise() == null
                        ? null : issue.getCruise().getCruiseId())
                .shipId(issue.getShip() == null
                        ? null : issue.getShip().getShipId())
                .cabinId(issue.getCabin() == null
                        ? null : issue.getCabin().getCabinId())
                .issueType(issue.getIssueType())
                .severity(issue.getSeverity())
                .description(issue.getDescription())
                .reportedAt(issue.getReportedAt())
                .resolvedAt(issue.getResolvedAt())
                .status(issue.getStatus())
                .reportedById(issue.getReportedBy() == null
                        ? null : issue.getReportedBy().getUserId())
                .resolvedById(issue.getResolvedBy() == null
                        ? null : issue.getResolvedBy().getUserId())
                .assignedToId(issue.getAssignedTo() == null
                        ? null : issue.getAssignedTo().getUserId())
                .verifiedById(issue.getVerifiedBy() == null
                        ? null : issue.getVerifiedBy().getUserId())
                .verifiedAt(issue.getVerifiedAt())
                .resolution(issue.getResolution())
                .createdAt(issue.getCreatedAt())
                .updatedAt(issue.getUpdatedAt())
                .build();
    }
}
