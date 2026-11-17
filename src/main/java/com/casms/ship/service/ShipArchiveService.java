package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipArchiveService {
    private final ShipRepository ships;
    private final ShipService shipService;
    private final JdbcTemplate jdbc;

    public record Eligibility(boolean eligible, List<String> blockers) {}

    @Transactional(readOnly = true)
    public Eligibility eligibility(UUID id) {
        UUID actor = requireActor();
        Ship ship = ships.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ship", id));
        requireScope(id, actor);
        return evaluate(ship);
    }

    // The dependency triggers take a conflicting row lock on this same ship before adding a reference.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShipResponse archive(UUID id) {
        UUID actor = requireActor();
        Ship ship = ships.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Ship", id));
        requireScope(id, actor);
        if (ship.getArchivedAt() == null) {
            Eligibility eligibility = evaluate(ship);
            if (!eligibility.eligible()) {
                throw new BusinessException(HttpStatus.CONFLICT, "SHIP_ARCHIVE_BLOCKED",
                        "The ship is not eligible for archive", Map.of("blockers", eligibility.blockers()));
            }
            ship.setArchivedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            ship.setArchivedBy(actor);
            ships.flush();
        }
        return shipService.detail(id);
    }

    private Eligibility evaluate(Ship ship) {
        if (ship.getArchivedAt() != null) return new Eligibility(false, List.of("ALREADY_ARCHIVED"));
        if (ship.getStatus() != ShipStatus.DRAFT) return new Eligibility(false, List.of("NOT_DRAFT"));
        List<String> blockers = jdbc.queryForList(
                "SELECT blocker FROM casms_ship_archive_blockers(?) ORDER BY blocker", String.class, ship.getShipId());
        return new Eligibility(blockers.isEmpty(), List.copyOf(blockers));
    }

    static UUID requireActor() {
        UUID actor = CruisePlanOperationsAccess.currentShipManager();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (actor == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SHIP_ARCHIVE"))) {
            throw new AccessDeniedException("Ship Manager archive permission is required");
        }
        return actor;
    }

    private void requireScope(UUID id, UUID actor) {
        if (!ships.isManagedBy(id, actor)) throw new AccessDeniedException("Ship is outside the manager's scope");
    }

    static void requireUnarchived(Ship ship) {
        if (ship.getArchivedAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_ARCHIVED", "The ship has been archived");
        }
    }
}
