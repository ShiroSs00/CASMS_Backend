package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.OperationalRegulationRequest;
import com.casms.ship.dto.response.OperationalRegulationResponse;
import com.casms.ship.dto.response.RegulationConflictComparedItem;
import com.casms.ship.dto.response.RegulationConflictResponse;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.repository.CruiseRegulationRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OperationalRegulationService {

    private final OperationalRegulationRepository regulationRepository;
    private final ShipRegulationRepository shipRegulationRepository;
    private final CruiseRegulationRepository cruiseRegulationRepository;
    private final BusinessCodeGenerator businessCodeGenerator;

    // ==================== CREATE ====================

    public OperationalRegulationResponse createRegulation(
            @Valid OperationalRegulationRequest request
    ) {
        validateRequest(request);

        OperationalRegulation regulation = new OperationalRegulation();
        applyRequest(regulation, request);
        regulation.setRegulationCode(businessCodeGenerator.next("RG"));
        regulation.setStatus(RegulationStatus.DRAFT);

        return toResponse(regulationRepository.save(regulation));
    }

    // ==================== LIST ====================

    @Transactional(readOnly = true)
    public List<OperationalRegulationResponse> listRegulations() {
        return regulationRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ==================== DETAIL ====================

    @Transactional(readOnly = true)
    public OperationalRegulationResponse getRegulationDetail(
            UUID regulationId
    ) {
        return toResponse(findRegulation(regulationId));
    }

    // ==================== UPDATE ====================

    public OperationalRegulationResponse updateRegulation(
            UUID regulationId,
            @Valid OperationalRegulationRequest request
    ) {
        OperationalRegulation regulation = findRegulation(regulationId);
        validateRequest(request);
        applyRequest(regulation, request);

        return toResponse(regulationRepository.save(regulation));
    }

    // ==================== DELETE ====================

    public void deleteUnusedRegulation(UUID regulationId) {
        OperationalRegulation regulation = findRegulation(regulationId);

        boolean assignedToShip =
                shipRegulationRepository
                        .existsByRegulation_RegulationId(regulationId);

        boolean assignedToCruise =
                cruiseRegulationRepository
                        .existsByRegulation_RegulationId(regulationId);

        if (assignedToShip || assignedToCruise) {
            throw new IllegalStateException(
                    "Cannot delete a regulation assigned to a ship or cruise."
            );
        }

        regulationRepository.delete(regulation);
    }

    // ==================== ACTIVATE ====================

    public OperationalRegulationResponse activateRegulation(
            UUID regulationId
    ) {
        OperationalRegulation regulation = findRegulation(regulationId);

        validateDateRange(
                regulation.getEffectiveFrom(),
                regulation.getEffectiveTo()
        );

        Instant now = Instant.now();

        if (regulation.getEffectiveFrom().isAfter(now)) {
            throw new IllegalStateException(
                    "Cannot activate a regulation before its effective date."
            );
        }

        if (regulation.getEffectiveTo() != null
                && regulation.getEffectiveTo().isBefore(now)) {
            throw new IllegalStateException(
                    "Cannot activate an expired regulation."
            );
        }

        RegulationConflictResponse verdict = getConflictVerdict(regulationId, "ACTIVATE");
        if (Boolean.FALSE.equals(verdict.getCanProceed())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    verdict.getCode() != null ? verdict.getCode() : "REGULATION_CONFLICT_BLOCKING",
                    verdict.getMessage()
            );
        }

        regulation.setStatus(RegulationStatus.ACTIVE);

        return toResponse(regulationRepository.save(regulation));
    }

    // ==================== DEACTIVATE ====================

    public OperationalRegulationResponse deactivateRegulation(
            UUID regulationId
    ) {
        OperationalRegulation regulation = findRegulation(regulationId);

        if (regulation.getStatus() != RegulationStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active regulations can be deactivated."
            );
        }

        regulation.setStatus(RegulationStatus.INACTIVE);

        return toResponse(regulationRepository.save(regulation));
    }

    // ==================== EFFECTIVE REGULATIONS ====================

    @Transactional(readOnly = true)
    public List<OperationalRegulationResponse> getEffectiveRegulations() {
        return getEffectiveRegulations(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<OperationalRegulationResponse> getEffectiveRegulations(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException("Effective date must not be null.");
        }

        return regulationRepository.findAll()
                .stream()
                .filter(regulation -> regulation.getStatus() == RegulationStatus.ACTIVE)
                .filter(regulation -> !regulation.getEffectiveFrom().isAfter(at))
                .filter(regulation -> regulation.getEffectiveTo() == null || !regulation.getEffectiveTo().isBefore(at))
                .map(this::toResponse)
                .toList();
    }

    // ==================== VALIDATE REGULATION ====================

    @Transactional(readOnly = true)
    public void validateRegulation(UUID regulationId) {
        OperationalRegulation regulation = findRegulation(regulationId);

        validateRequiredFields(regulation);
        validateDateRange(
                regulation.getEffectiveFrom(),
                regulation.getEffectiveTo()
        );

        if (regulationRepository.existsByRegulationCodeAndRegulationIdNot(
                regulation.getRegulationCode(),
                regulationId
        )) {
            throw new IllegalArgumentException(
                    "Regulation code already exists."
            );
        }
    }

    // ==================== CONFLICT VERDICT ====================

    @Transactional(readOnly = true)
    public RegulationConflictResponse getConflictVerdict(
            UUID regulationId,
            String context
    ) {
        String effectiveContext = (context != null && !context.isBlank())
                ? context.trim().toUpperCase()
                : "ACTIVATE";

        OperationalRegulation target = findRegulation(regulationId);

        List<OperationalRegulation> candidates = regulationRepository
                .findByRegulationTypeAndScope(
                        target.getRegulationType(),
                        target.getScope()
                )
                .stream()
                .filter(other ->
                        !other.getRegulationId()
                                .equals(target.getRegulationId()))
                .filter(other ->
                        other.getStatus() != RegulationStatus.INACTIVE
                                && other.getStatus() != RegulationStatus.EXPIRED)
                .filter(other ->
                        overlaps(
                                target.getEffectiveFrom(),
                                target.getEffectiveTo(),
                                other.getEffectiveFrom(),
                                other.getEffectiveTo()
                        ))
                .toList();

        if (candidates.isEmpty()) {
            return RegulationConflictResponse.builder()
                    .context(effectiveContext)
                    .severity("CLEAR")
                    .canProceed(true)
                    .code("CLEAR")
                    .message("Không phát hiện xung đột quy định.")
                    .comparedWith(List.of())
                    .build();
        }

        List<RegulationConflictComparedItem> comparedItems = candidates.stream()
                .map(cand -> RegulationConflictComparedItem.builder()
                        .regulationId(cand.getRegulationId())
                        .code(cand.getRegulationCode())
                        .scope(cand.getScope())
                        .value(cand.getValue())
                        .unit(cand.getUnit() != null ? cand.getUnit().name() : null)
                        .build())
                .toList();

        boolean hasConflictingValue = candidates.stream()
                .anyMatch(c -> !Objects.equals(c.getValue(), target.getValue()));

        if (hasConflictingValue) {
            return RegulationConflictResponse.builder()
                    .context(effectiveContext)
                    .severity("BLOCKING")
                    .canProceed(false)
                    .code("REGULATION_CONFLICT_BLOCKING")
                    .message("Xung đột giá trị với quy định hiện hành đang có hiệu lực.")
                    .comparedWith(comparedItems)
                    .build();
        }

        return RegulationConflictResponse.builder()
                .context(effectiveContext)
                .severity("WARNING")
                .canProceed(true)
                .code("REGULATION_OVERLAP_REVIEW")
                .message("Có các khoảng hiệu lực cần được rà soát.")
                .comparedWith(comparedItems)
                .build();
    }

    // ==================== PRIVATE HELPERS ====================

    public OperationalRegulation findRegulation(UUID regulationId) {
        if (regulationId == null) {
            throw new IllegalArgumentException(
                    "Regulation ID must not be null."
            );
        }

        return regulationRepository.findById(regulationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Operational regulation not found: " + regulationId
                ));
    }

    private void validateRequest(OperationalRegulationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Regulation request must not be null."
            );
        }

        validateRequiredFields(
                request.getName(),
                request.getRegulationType(),
                request.getScope(),
                request.getValue(),
                request.getEffectiveFrom()
        );

        validateDateRange(
                request.getEffectiveFrom(),
                request.getEffectiveTo()
        );
    }

    private void validateRequiredFields(OperationalRegulation regulation) {
        if (regulation.getRegulationCode() == null || regulation.getRegulationCode().isBlank()) {
            throw new IllegalArgumentException("Regulation code is required.");
        }
        if (regulation.getRegulationCode().trim().length() > 30) {
            throw new IllegalArgumentException("Regulation code must not exceed 30 characters.");
        }
        validateRequiredFields(
                regulation.getName(),
                regulation.getRegulationType(),
                regulation.getScope(),
                regulation.getValue(),
                regulation.getEffectiveFrom()
        );
    }

    private void validateRequiredFields(
            String name,
            Object type,
            Object scope,
            String value,
            Instant effectiveFrom
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Regulation name is required."
            );
        }

        if (name.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Regulation name must not exceed 150 characters."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Regulation type is required."
            );
        }

        if (scope == null) {
            throw new IllegalArgumentException(
                    "Regulation scope is required."
            );
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Regulation value is required."
            );
        }

        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "Effective-from date is required."
            );
        }
    }

    private void validateDateRange(
            Instant effectiveFrom,
            Instant effectiveTo
    ) {
        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "Effective-from date is required."
            );
        }

        if (effectiveTo != null
                && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "Effective-to date must be later than effective-from date."
            );
        }
    }

    private boolean overlaps(
            Instant start1,
            Instant end1,
            Instant start2,
            Instant end2
    ) {
        Instant normalizedEnd1 = end1 == null
                ? Instant.MAX
                : end1;

        Instant normalizedEnd2 = end2 == null
                ? Instant.MAX
                : end2;

        return !start1.isAfter(normalizedEnd2)
                && !start2.isAfter(normalizedEnd1);
    }

    private void applyRequest(
            OperationalRegulation target,
            OperationalRegulationRequest request
    ) {
        target.setName(request.getName().trim());
        target.setDescription(request.getDescription());
        target.setRegulationType(request.getRegulationType());
        target.setScope(request.getScope());
        target.setValue(request.getValue().trim());
        target.setUnit(request.getUnit());
        target.setEffectiveFrom(request.getEffectiveFrom());
        target.setEffectiveTo(request.getEffectiveTo());
    }

    public OperationalRegulationResponse toResponse(
            OperationalRegulation regulation
    ) {
        return OperationalRegulationResponse.builder()
                .regulationId(regulation.getRegulationId())
                .regulationCode(regulation.getRegulationCode())
                .name(regulation.getName())
                .description(regulation.getDescription())
                .regulationType(regulation.getRegulationType())
                .scope(regulation.getScope())
                .value(regulation.getValue())
                .unit(regulation.getUnit())
                .effectiveFrom(regulation.getEffectiveFrom())
                .effectiveTo(regulation.getEffectiveTo())
                .status(regulation.getStatus())
                .createdAt(regulation.getCreatedAt())
                .updatedAt(regulation.getUpdatedAt())
                .build();
    }
}
