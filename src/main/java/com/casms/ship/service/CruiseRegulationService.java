package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.dto.response.RegulationAssignmentSnapshotResponse;
import com.casms.ship.dto.response.RegulationConflictComparedItem;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.dto.response.CruiseRegulationResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseRegulation;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.repository.CruiseRegulationRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.OperationalRegulationRepository;
import com.casms.ship.repository.ShipRegulationRepository;
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
public class CruiseRegulationService {

    private final CruiseRegulationRepository cruiseRegulationRepository;
    private final CruiseRepository cruiseRepository;
    private final ShipRegulationRepository shipRegulationRepository;
    private final OperationalRegulationRepository operationalRegulationRepository;
    private final OperationalRegulationService operationalRegulationService;

    /**
     * Gán quy định cho chuyến đi.
     */
    public CruiseRegulationResponse assign(@Valid CruiseRegulationRequest request) {
        validateRequest(request);

        UUID cruiseId = request.getCruiseId();
        UUID regulationId = request.getRegulationId();

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cruise not found: " + cruiseId
                ));

        OperationalRegulation regulation =
                operationalRegulationRepository.findById(regulationId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Operational regulation not found: " + regulationId
                        ));

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "GLOBAL regulations apply automatically to all cruises and must not be assigned individually."
            );
        }

        RegulationConflictResponse verdict = precheck(cruiseId, regulationId);
        if (Boolean.FALSE.equals(verdict.getCanProceed())) {
            HttpStatus status = "REGULATION_ALREADY_ASSIGNED".equals(verdict.getCode())
                    ? HttpStatus.CONFLICT
                    : HttpStatus.UNPROCESSABLE_ENTITY;
            throw new BusinessException(status, verdict.getCode(), verdict.getMessage());
        }

        CruiseRegulation relation = new CruiseRegulation();
        relation.setCruise(cruise);
        relation.setRegulation(regulation);

        CruiseRegulation saved = cruiseRegulationRepository.save(relation);
        return toResponse(saved);
    }

    /**
     * Precheck xung đột quy định trước khi gán cho chuyến đi.
     */
    @Transactional(readOnly = true)
    public RegulationConflictResponse precheck(UUID cruiseId, UUID regulationId) {
        if (cruiseId == null || regulationId == null) {
            throw new IllegalArgumentException("Cruise ID and Regulation ID must not be null.");
        }

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new EntityNotFoundException("Cruise not found: " + cruiseId));

        OperationalRegulation regulation = operationalRegulationRepository.findById(regulationId)
                .orElseThrow(() -> new EntityNotFoundException("Operational regulation not found: " + regulationId));

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_CRUISE")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("GLOBAL regulations apply automatically to all cruises and must not be assigned individually.")
                    .comparedWith(List.of())
                    .build();
        }

        if (regulation.getScope() != RegulationScope.CRUISE) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_CRUISE")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("Regulation scope is not applicable to cruises.")
                    .comparedWith(List.of())
                    .build();
        }

        if (cruiseRegulationRepository.existsByCruise_CruiseIdAndRegulation_RegulationId(cruiseId, regulationId)) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_CRUISE")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_ALREADY_ASSIGNED")
                    .message("This regulation is already assigned to the cruise.")
                    .comparedWith(List.of())
                    .build();
        }

        List<OperationalRegulation> directAssigned = cruiseRegulationRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(CruiseRegulation::getRegulation)
                .toList();

        List<OperationalRegulation> shipRegs = (cruise.getShip() != null)
                ? shipRegulationRepository.findByShip_ShipId(cruise.getShip().getShipId()).stream()
                .map(ShipRegulation::getRegulation)
                .toList()
                : List.of();

        List<OperationalRegulation> globalRegs = operationalRegulationRepository.findByScope(RegulationScope.GLOBAL);

        List<OperationalRegulation> candidates = Stream.of(directAssigned.stream(), shipRegs.stream(), globalRegs.stream())
                .flatMap(s -> s)
                .filter(r -> r.getStatus() == RegulationStatus.ACTIVE)
                .filter(r -> r.getRegulationType() == regulation.getRegulationType())
                .filter(r -> !r.getRegulationId().equals(regulation.getRegulationId()))
                .filter(r -> overlaps(regulation.getEffectiveFrom(), regulation.getEffectiveTo(), r.getEffectiveFrom(), r.getEffectiveTo()))
                .toList();

        if (candidates.isEmpty()) {
            return RegulationConflictResponse.builder()
                    .context("ASSIGN_CRUISE")
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
                    .context("ASSIGN_CRUISE")
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("Quy định xung đột giá trị với quy định hiện có trên chuyến đi.")
                    .comparedWith(comparedItems)
                    .build();
        }

        return RegulationConflictResponse.builder()
                .context("ASSIGN_CRUISE")
                .severity("WARNING")
                .canProceed(true)
                .code("REGULATION_OVERLAP_REVIEW")
                .message("Có các khoảng hiệu lực cần được rà soát.")
                .comparedWith(comparedItems)
                .build();
    }

    /**
     * Lấy quy định GLOBAL và quy định CRUISE được gán cho chuyến đi.
     */
    @Transactional(readOnly = true)
    public List<CruiseRegulationResponse> list(UUID cruiseId) {
        if (cruiseId == null) {
            throw new IllegalArgumentException("Cruise ID must not be null.");
        }

        if (!cruiseRepository.existsById(cruiseId)) {
            throw new EntityNotFoundException("Cruise not found: " + cruiseId);
        }

        List<CruiseRegulationResponse> globalRegulations =
                operationalRegulationRepository
                        .findByScope(RegulationScope.GLOBAL)
                        .stream()
                        .map(regulation -> toResponse(cruiseId, regulation.getRegulationId()))
                        .toList();

        List<CruiseRegulationResponse> cruiseRegulations =
                cruiseRegulationRepository.findByCruise_CruiseId(cruiseId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return Stream.concat(
                globalRegulations.stream(),
                cruiseRegulations.stream()
        ).toList();
    }

    /**
     * Snapshot quy định hiệu lực (direct và inherited) cho chuyến đi.
     */
    @Transactional(readOnly = true)
    public RegulationAssignmentSnapshotResponse getEffectiveSnapshot(UUID cruiseId, Instant at) {
        if (cruiseId == null) {
            throw new IllegalArgumentException("Cruise ID must not be null.");
        }

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new EntityNotFoundException("Cruise not found: " + cruiseId));

        Instant effectiveAt = at != null ? at : Instant.now();

        List<CruiseRegulation> directRelations = cruiseRegulationRepository.findByCruise_CruiseId(cruiseId);
        List<RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse> directItems = directRelations.stream()
                .map(CruiseRegulation::getRegulation)
                .filter(r -> isEffectiveAt(r, effectiveAt))
                .map(r -> RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse.builder()
                        .regulation(operationalRegulationService.toResponse(r))
                        .source("CRUISE")
                        .sourceHostId(cruise.getCruiseId())
                        .sourceLabel("CRUISE · " + cruise.getCruiseCode())
                        .canRemove(true)
                        .removeBlockedReason(null)
                        .build())
                .toList();

        Set<UUID> directIds = directItems.stream()
                .map(d -> d.getRegulation().getRegulationId())
                .collect(Collectors.toSet());

        List<RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse> inheritedItems = new ArrayList<>();

        if (cruise.getShip() != null) {
            Ship ship = cruise.getShip();
            List<ShipRegulation> shipRelations = shipRegulationRepository.findByShip_ShipId(ship.getShipId());
            for (ShipRegulation sr : shipRelations) {
                OperationalRegulation r = sr.getRegulation();
                if (isEffectiveAt(r, effectiveAt) && !directIds.contains(r.getRegulationId())) {
                    inheritedItems.add(RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse.builder()
                            .regulation(operationalRegulationService.toResponse(r))
                            .source("SHIP")
                            .sourceHostId(ship.getShipId())
                            .sourceLabel("SHIP · " + ship.getShipCode())
                            .canRemove(false)
                            .removeBlockedReason("Quy định kế thừa chỉ đọc tại Cruise.")
                            .build());
                }
            }
        }

        Set<UUID> inheritedIds = inheritedItems.stream()
                .map(i -> i.getRegulation().getRegulationId())
                .collect(Collectors.toSet());

        List<OperationalRegulation> globalRegs = operationalRegulationRepository.findByScope(RegulationScope.GLOBAL);
        for (OperationalRegulation gr : globalRegs) {
            if (isEffectiveAt(gr, effectiveAt) && !directIds.contains(gr.getRegulationId()) && !inheritedIds.contains(gr.getRegulationId())) {
                inheritedItems.add(RegulationAssignmentSnapshotResponse.RegulationAssignmentItemResponse.builder()
                        .regulation(operationalRegulationService.toResponse(gr))
                        .source("GLOBAL")
                        .sourceHostId(null)
                        .sourceLabel("GLOBAL")
                        .canRemove(false)
                        .removeBlockedReason("Quy định kế thừa chỉ đọc tại Cruise.")
                        .build());
            }
        }

        return RegulationAssignmentSnapshotResponse.builder()
                .host(RegulationAssignmentSnapshotResponse.HostSummary.builder()
                        .type("CRUISE")
                        .id(cruise.getCruiseId())
                        .code(cruise.getCruiseCode())
                        .name(cruise.getCruiseName())
                        .build())
                .checkedAt(effectiveAt)
                .direct(directItems)
                .inherited(inheritedItems)
                .build();
    }

    /**
     * Xóa liên kết giữa chuyến đi và quy định.
     */
    public void remove(UUID cruiseId, UUID regulationId) {
        if (cruiseId == null || regulationId == null) {
            throw new IllegalArgumentException("Cruise ID and Regulation ID must not be null.");
        }

        CruiseRegulation.CruiseRegulationId id =
                new CruiseRegulation.CruiseRegulationId();

        id.setCruise(cruiseId);
        id.setRegulation(regulationId);

        CruiseRegulation relation = cruiseRegulationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cruise regulation assignment not found."));

        cruiseRegulationRepository.delete(relation);
    }

    public void validateApplicableRegulation(OperationalRegulation regulation) {
        if (regulation == null) {
            throw new IllegalArgumentException("Operational regulation must not be null.");
        }
        RegulationScope scope = regulation.getScope();
        if (scope != RegulationScope.CRUISE && scope != RegulationScope.GLOBAL) {
            throw new IllegalArgumentException("Regulation scope is not applicable to cruises.");
        }
    }

    public void checkRegulationConflict(UUID cruiseId, UUID regulationId) {
        if (cruiseId == null || regulationId == null) {
            throw new IllegalArgumentException("Cruise ID and Regulation ID must not be null.");
        }
        boolean alreadyAssigned = cruiseRegulationRepository.existsByCruise_CruiseIdAndRegulation_RegulationId(cruiseId, regulationId);
        if (alreadyAssigned) {
            throw new IllegalStateException("This regulation is already assigned to the cruise.");
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

    private void validateRequest(CruiseRegulationRequest request) {
        if (request == null
                || request.getCruiseId() == null
                || request.getRegulationId() == null) {
            throw new IllegalArgumentException("Cruise ID and Regulation ID must not be null.");
        }
    }

    private CruiseRegulationResponse toResponse(CruiseRegulation entity) {
        return CruiseRegulationResponse.builder()
                .cruiseId(entity.getCruise().getCruiseId())
                .regulationId(entity.getRegulation().getRegulationId())
                .build();
    }

    private CruiseRegulationResponse toResponse(UUID cruiseId, UUID regulationId) {
        return CruiseRegulationResponse.builder()
                .cruiseId(cruiseId)
                .regulationId(regulationId)
                .build();
    }
}
