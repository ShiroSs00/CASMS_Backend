package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.dto.response.RegulationAssignmentSnapshotResponse;
import com.casms.ship.dto.response.RegulationConflictComparedItem;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.dto.response.ShipRegulationResponse;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.repository.OperationalRegulationRepository;
import com.casms.ship.repository.ShipRegulationRepository;
import com.casms.ship.repository.ShipRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ShipRegulationService {

    private final ShipRegulationRepository shipRegulationRepository;
    private final ShipRepository shipRepository;
    private final OperationalRegulationRepository operationalRegulationRepository;
    private final OperationalRegulationService operationalRegulationService;

    /**
     * Gán quy định cho tàu.
     */
    public ShipRegulationResponse assign(@Valid ShipRegulationRequest request) {
        validateRequest(request);

        UUID shipId = request.getShipId();
        UUID regulationId = request.getRegulationId();

        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ship not found: " + shipId
                ));

        OperationalRegulation regulation =
                operationalRegulationRepository.findById(regulationId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Operational regulation not found: " + regulationId
                        ));

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "GLOBAL regulations apply automatically to all ships and must not be assigned individually."
            );
        }

        RegulationConflictResponse verdict = precheck(shipId, regulationId);
        if (Boolean.FALSE.equals(verdict.getCanProceed())) {
            HttpStatus status = "REGULATION_ALREADY_ASSIGNED".equals(verdict.getCode())
                    ? HttpStatus.CONFLICT
                    : HttpStatus.UNPROCESSABLE_ENTITY;
            throw new BusinessException(status, verdict.getCode(), verdict.getMessage());
        }

        ShipRegulation relation = new ShipRegulation();
        relation.setShip(ship);
        relation.setRegulation(regulation);

        ShipRegulation saved = shipRegulationRepository.save(relation);
        return toResponse(saved);
    }

    /**
     * Kiểm tra conflict trước khi gán quy định cho tàu.
     */
    @Transactional(readOnly = true)
    public RegulationConflictResponse precheck(UUID shipId, UUID regulationId) {
        if (shipId == null || regulationId == null) {
            throw new IllegalArgumentException("Ship ID and Regulation ID must not be null.");
        }

        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new EntityNotFoundException("Ship not found: " + shipId));

        OperationalRegulation regulation = operationalRegulationRepository.findById(regulationId)
                .orElseThrow(() -> new EntityNotFoundException("Operational regulation not found: " + regulationId));

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_SHIP")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("GLOBAL regulations apply automatically to all ships and must not be assigned individually.")
                    .comparedWith(List.of())
                    .build();
        }

        if (regulation.getScope() != RegulationScope.SHIP) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_SHIP")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("Regulation scope is not applicable to ships.")
                    .comparedWith(List.of())
                    .build();
        }

        if (shipRegulationRepository.existsByShip_ShipIdAndRegulation_RegulationId(shipId, regulationId)) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_SHIP")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_ALREADY_ASSIGNED")
                    .message("This regulation is already assigned to the ship.")
                    .comparedWith(List.of())
                    .build();
        }

        List<OperationalRegulation> directAssigned = shipRegulationRepository.findByShip_ShipId(shipId).stream()
                .map(ShipRegulation::getRegulation)
                .toList();
        List<OperationalRegulation> globalRegs = operationalRegulationRepository.findByScope(RegulationScope.GLOBAL);

        List<OperationalRegulation> candidates = Stream.concat(directAssigned.stream(), globalRegs.stream())
                .filter(r -> r.getStatus() == RegulationStatus.ACTIVE)
                .filter(r -> r.getRegulationType() == regulation.getRegulationType())
                .filter(r -> !r.getRegulationId().equals(regulation.getRegulationId()))
                .filter(r -> overlaps(regulation.getEffectiveFrom(), regulation.getEffectiveTo(), r.getEffectiveFrom(), r.getEffectiveTo()))
                .toList();

        if (candidates.isEmpty()) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_SHIP")
                    .severity("CLEAR")
                    .canProceed(true)
                    .code("CLEAR")
                    .message("Không phát hiện xung đột.")
                    .comparedWith(List.of())
                    .build();
        }

        List<RegulationConflictComparedItem> comparedItems = candidates.stream()
                .map(c -> RegulationConflictComparedItem.builder()
                        .regulationId(c.getRegulationId())
                        .code(c.getRegulationCode())
                        .scope(c.getScope())
                        .value(c.getValue())
                        .unit(c.getUnit() != null ? c.getUnit().name() : null)
                        .build())
                .toList();

        boolean hasConflictingValue = candidates.stream()
                .anyMatch(c -> !Objects.equals(c.getValue(), regulation.getValue()));

        if (hasConflictingValue) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_SHIP")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("Quy định xung đột giá trị với quy định hiện có trên tàu.")
                    .comparedWith(comparedItems)
                    .build();
        }

        return RegulationConflictResponse.builder()
                .context("ASSIGN_SHIP")
                .severity("WARNING")
                .canProceed(true)
                .code("REGULATION_OVERLAP_REVIEW")
                .message("Có các khoảng hiệu lực cần được rà soát.")
                .comparedWith(comparedItems)
                .build();
    }

    /**
     * Lấy các quy định GLOBAL và quy định SHIP được gán cho tàu.
     */
    @Transactional(readOnly = true)
    public List<ShipRegulationResponse> list(UUID shipId) {
        if (shipId == null) {
            throw new IllegalArgumentException("Ship ID must not be null.");
        }

        if (!shipRepository.existsById(shipId)) {
            throw new EntityNotFoundException("Ship not found: " + shipId);
        }

        List<ShipRegulationResponse> globalRegulations =
                operationalRegulationRepository
                        .findByScope(RegulationScope.GLOBAL)
                        .stream()
                        .map(regulation -> toResponse(shipId, regulation.getRegulationId()))
                        .toList();

        List<ShipRegulationResponse> shipRegulations =
                shipRegulationRepository.findByShip_ShipId(shipId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return Stream.concat(
                globalRegulations.stream(),
                shipRegulations.stream()
        ).toList();
    }

    /**
     * Snapshot các quy định đang có hiệu lực (direct và inherited).
     */
    @Transactional(readOnly = true)
    public RegulationAssignmentSnapshotResponse getEffectiveSnapshot(UUID shipId, Instant at) {
        if (shipId == null) {
            throw new IllegalArgumentException("Ship ID must not be null.");
        }

        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new EntityNotFoundException("Ship not found: " + shipId));

        Instant effectiveAt = at != null ? at : Instant.now();

        List<ShipRegulation> directRelations = shipRegulationRepository.findByShip_ShipId(shipId);
        List<RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse> directItems = directRelations.stream()
                .map(ShipRegulation::getRegulation)
                .filter(r -> isEffectiveAt(r, effectiveAt))
                .map(r -> RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse.builder()
                        .regulation(operationalRegulationService.toResponse(r))
                        .source("SHIP")
                        .sourceHostId(ship.getShipId())
                        .sourceLabel("SHIP · " + ship.getShipCode())
                        .canRemove(true)
                        .removeBlockedReason(null)
                        .build())
                .toList();

        Set<UUID> directIds = directItems.stream()
                .map(i -> i.getRegulation().getRegulationId())
                .collect(Collectors.toSet());

        List<RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse> inheritedItems = operationalRegulationRepository
                .findByScope(RegulationScope.GLOBAL)
                .stream()
                .filter(r -> isEffectiveAt(r, effectiveAt))
                .filter(r -> !directIds.contains(r.getRegulationId()))
                .map(r -> RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse.builder()
                        .regulation(operationalRegulationService.toResponse(r))
                        .source("GLOBAL")
                        .sourceHostId(null)
                        .sourceLabel("GLOBAL")
                        .canRemove(false)
                        .removeBlockedReason("Quy định áp dụng toàn hệ thống, không thể gỡ.")
                        .build())
                .toList();

        return RegulationAssignmentSnapshotResponse.builder()
                .host(RegulationAssignmentSnapshotResponse.HostSummary.builder()
                        .type("SHIP")
                        .id(ship.getShipId())
                        .code(ship.getShipCode())
                        .name(ship.getShipName())
                        .build())
                .checkedAt(effectiveAt)
                .direct(directItems)
                .inherited(inheritedItems)
                .build();
    }

    /**
     * Xóa liên kết giữa tàu và quy định.
     */
    public void remove(UUID shipId, UUID regulationId) {
        if (shipId == null || regulationId == null) {
            throw new IllegalArgumentException("Ship ID and Regulation ID must not be null.");
        }

        ShipRegulation.ShipRegulationId id = new ShipRegulation.ShipRegulationId();
        id.setShip(shipId);
        id.setRegulation(regulationId);

        ShipRegulation relation = shipRegulationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Ship regulation assignment not found."));

        shipRegulationRepository.delete(relation);
    }

    public void validateApplicableRegulation(OperationalRegulation regulation) {
        if (regulation == null) {
            throw new IllegalArgumentException("Operational regulation must not be null.");
        }
        RegulationScope scope = regulation.getScope();
        if (scope != RegulationScope.SHIP && scope != RegulationScope.GLOBAL) {
            throw new IllegalArgumentException("Regulation scope is not applicable to ships.");
        }
    }

    public void checkRegulationConflict(UUID shipId, UUID regulationId) {
        if (shipId == null || regulationId == null) {
            throw new IllegalArgumentException("Ship ID and Regulation ID must not be null.");
        }
        boolean alreadyAssigned = shipRegulationRepository.existsByShip_ShipIdAndRegulation_RegulationId(shipId, regulationId);
        if (alreadyAssigned) {
            throw new IllegalStateException("This regulation is already assigned to the ship.");
        }
    }

    private boolean isEffectiveAt(OperationalRegulation r, Instant at) {
        if (r.getStatus() != RegulationStatus.ACTIVE) {
            return false;
        }
        if (r.getEffectiveFrom().isAfter(at)) {
            return false;
        }
        return r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(at);
    }

    private boolean overlaps(Instant start1, Instant end1, Instant start2, Instant end2) {
        Instant normalizedEnd1 = end1 == null ? Instant.MAX : end1;
        Instant normalizedEnd2 = end2 == null ? Instant.MAX : end2;
        return !start1.isAfter(normalizedEnd2) && !start2.isAfter(normalizedEnd1);
    }

    private void validateRequest(ShipRegulationRequest request) {
        if (request == null
                || request.getShipId() == null
                || request.getRegulationId() == null) {
            throw new IllegalArgumentException("Ship ID and Regulation ID must not be null.");
        }
    }

    private ShipRegulationResponse toResponse(ShipRegulation entity) {
        return ShipRegulationResponse.builder()
                .shipId(entity.getShip().getShipId())
                .regulationId(entity.getRegulation().getRegulationId())
                .build();
    }

    /**
     * Tạo response dto cho quy định GLOBAL.
     */
    private ShipRegulationResponse toResponse(
            UUID shipId,
            UUID regulationId
    ) {
        return ShipRegulationResponse.builder()
                .shipId(shipId)
                .regulationId(regulationId)
                .build();
    }
}
